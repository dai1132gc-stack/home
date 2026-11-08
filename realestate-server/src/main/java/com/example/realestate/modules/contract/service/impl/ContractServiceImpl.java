package com.example.realestate.modules.contract.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.ContractStatus;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.HouseStatus;
import com.example.realestate.common.enums.PaymentCycle;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.common.util.IdCardMasker;
import com.example.realestate.infrastructure.pdf.ChineseAmountConverter;
import com.example.realestate.infrastructure.pdf.ContractPdfGenerator;
import com.example.realestate.modules.contract.convert.ContractConvert;
import com.example.realestate.modules.contract.mapper.ContractMapper;
import com.example.realestate.modules.contract.mapper.ContractPartyMapper;
import com.example.realestate.modules.contract.mapper.ContractStatusLogMapper;
import com.example.realestate.modules.contract.mapper.ContractTemplateMapper;
import com.example.realestate.modules.contract.model.ContractSnapshot;
import com.example.realestate.modules.contract.model.dto.ContractCreateRequest;
import com.example.realestate.modules.contract.model.dto.ContractTerminateRequest;
import com.example.realestate.modules.contract.model.dto.ContractUpdateRequest;
import com.example.realestate.modules.contract.model.entity.Contract;
import com.example.realestate.modules.contract.model.entity.ContractParty;
import com.example.realestate.modules.contract.model.entity.ContractStatusLog;
import com.example.realestate.modules.contract.model.entity.ContractTemplate;
import com.example.realestate.modules.contract.model.query.ContractQuery;
import com.example.realestate.modules.contract.model.vo.ContractDetailVO;
import com.example.realestate.modules.contract.model.vo.ContractListItemVO;
import com.example.realestate.modules.contract.model.vo.ContractPartyVO;
import com.example.realestate.modules.contract.model.vo.ContractStatusLogVO;
import com.example.realestate.modules.contract.service.ContractService;
import com.example.realestate.modules.appointment.mapper.ViewingAppointmentMapper;
import com.example.realestate.modules.appointment.model.entity.ViewingAppointment;
import com.example.realestate.modules.file.service.PrivateStorageService;
import com.example.realestate.modules.house.mapper.HouseMapper;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.house.service.HouseLifecycleService;
import com.example.realestate.modules.owner.model.entity.OwnerProfile;
import com.example.realestate.modules.owner.service.OwnerService;
import com.example.realestate.modules.transaction.model.vo.TransactionVO;
import com.example.realestate.modules.transaction.service.TransactionService;
import com.example.realestate.modules.user.mapper.SysUserMapper;
import com.example.realestate.modules.user.model.entity.SysUser;
import com.example.realestate.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 合同实现：
 *  - 创建时写入房源/双方/条款快照，历史合同不随房源修改变化；
 *  - 提交与生效前强制成功生成 PDF（草稿阶段失败不阻塞）；
 *  - 生效使用「房源行锁 + 条件 UPDATE + 生成列唯一键」三重保护，并发只允许一个成功；
 *  - 合同终止时按规则释放房源（CONTRACTED → PUBLISHED）。
 */
@Service
public class ContractServiceImpl implements ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractServiceImpl.class);

    private static final String DEFAULT_TEMPLATE_CODE = "STANDARD_LEASE";
    private static final String DEFAULT_CLAUSES = """
            第一条 租赁房屋
            出租方将坐落于{address}的房屋出租给承租方居住使用。
            第二条 租赁期限
            租赁期限自{startDate}起至{endDate}止。
            第三条 租金与押金
            月租金为人民币{amount}，付款方式为{paymentCycle}，押金为人民币{deposit}。
            """;

    private final ContractMapper contractMapper;
    private final ContractPartyMapper partyMapper;
    private final ContractStatusLogMapper statusLogMapper;
    private final ContractTemplateMapper templateMapper;
    private final SysUserMapper sysUserMapper;
    private final ViewingAppointmentMapper appointmentMapper;
    private final HouseMapper houseMapper;
    private final OwnerService ownerService;
    private final HouseLifecycleService houseLifecycle;
    private final TransactionService transactionService;
    private final ContractPdfGenerator pdfGenerator;
    private final PrivateStorageService privateStorage;
    private final ContractConvert contractConvert;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUser;

    public ContractServiceImpl(ContractMapper contractMapper,
                               ContractPartyMapper partyMapper,
                               ContractStatusLogMapper statusLogMapper,
                               ContractTemplateMapper templateMapper,
                               SysUserMapper sysUserMapper,
                               ViewingAppointmentMapper appointmentMapper,
                               HouseMapper houseMapper,
                               OwnerService ownerService,
                               HouseLifecycleService houseLifecycle,
                               TransactionService transactionService,
                               ContractPdfGenerator pdfGenerator,
                               PrivateStorageService privateStorage,
                               ContractConvert contractConvert,
                               ObjectMapper objectMapper,
                               CurrentUserProvider currentUser) {
        this.contractMapper = contractMapper;
        this.partyMapper = partyMapper;
        this.statusLogMapper = statusLogMapper;
        this.templateMapper = templateMapper;
        this.sysUserMapper = sysUserMapper;
        this.appointmentMapper = appointmentMapper;
        this.houseMapper = houseMapper;
        this.ownerService = ownerService;
        this.houseLifecycle = houseLifecycle;
        this.transactionService = transactionService;
        this.pdfGenerator = pdfGenerator;
        this.privateStorage = privateStorage;
        this.contractConvert = contractConvert;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
    }

    @Override
    public PageResponse<ContractListItemVO> list(ContractQuery query) {
        boolean admin = currentUser.isAdmin();
        return doList(query, admin ? null : currentUser.requireUserId());
    }

    @Override
    public PageResponse<ContractListItemVO> listMine(ContractQuery query) {
        return doList(query, currentUser.requireUserId());
    }

    @Override
    public ContractDetailVO detail(Long id) {
        Contract contract = requireEntity(id);
        requireVisible(contract);
        return buildDetail(contract);
    }

    @Override
    @Transactional
    public ContractDetailVO create(ContractCreateRequest request) {
        House house = houseMapper.selectById(request.houseId());
        if (house == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "房源不存在");
        }
        HouseStatus houseStatus = HouseStatus.of(house.getStatus());
        if (houseStatus != HouseStatus.PUBLISHED && houseStatus != HouseStatus.RESERVED) {
            throw new BusinessException(ErrorCode.CONFLICT, "房源当前状态不可起草合同（需已发布或已预定）");
        }
        if (contractMapper.countOccupyingByHouse(house.getId(), null) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "该房源已有待签署或生效中的合同");
        }
        SysUser customer = sysUserMapper.selectById(request.customerId());
        if (customer == null || !SysUser.ROLE_CUSTOMER.equals(customer.getRole())
                || !SysUser.STATUS_ACTIVE.equals(customer.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "客户不存在");
        }
        validateTerms(request.startDate(), request.endDate(), request.amount(), request.deposit());

        OwnerProfile owner = ownerService.findEntityOrNull(house.getOwnerId());
        String lessorName = firstNonBlank(request.lessorName(), owner == null ? null : owner.getName());
        String lessorPhone = firstNonBlank(request.lessorPhone(), owner == null ? null : owner.getPhone());
        if (lessorName == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写出租方姓名");
        }
        if (lessorPhone == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写出租方联系电话");
        }
        String lesseeName = firstNonBlank(request.customerName(), customer.getNickname(), customer.getUsername());
        String lesseePhone = firstNonBlank(request.customerPhone(), customer.getPhone());
        if (lesseePhone == null) {
            lesseePhone = latestAppointmentPhone(house.getId(), customer.getId());
        }
        if (lesseePhone == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写承租方联系电话");
        }
        SysUser agent = currentUser.currentUserIdOrNull() == null
                ? null : sysUserMapper.selectById(currentUser.currentUserIdOrNull());
        String agentName = agent == null ? "居间方"
                : firstNonBlank(agent.getNickname(), agent.getUsername(), "居间方");
        String agentPhone = agent == null ? null : agent.getPhone();

        ContractTemplate template = activeTemplate();
        String templateVersion = template == null
                ? DEFAULT_TEMPLATE_CODE + "-v1"
                : template.getCode() + "-" + template.getVersion();
        String clauses = template == null ? DEFAULT_CLAUSES : template.getContent();

        List<ContractSnapshot.Party> partySnapshots = List.of(
                new ContractSnapshot.Party(ContractParty.TYPE_LESSOR, lessorName, lessorPhone,
                        IdCardMasker.mask(request.lessorIdCard())),
                new ContractSnapshot.Party(ContractParty.TYPE_LESSEE, lesseeName, lesseePhone,
                        IdCardMasker.mask(request.customerIdCard())),
                new ContractSnapshot.Party(ContractParty.TYPE_AGENT, agentName, agentPhone, null));

        Instant now = Instant.now();
        ContractSnapshot snapshot = new ContractSnapshot(
                templateVersion,
                now,
                new ContractSnapshot.House(house.getId(), house.getTitle(), house.getHouseType(),
                        house.getCommunity(), house.getRegion(), house.getAddress(), house.getArea(),
                        house.getRooms(), house.getBathrooms(), house.getFloor()),
                partySnapshots,
                new ContractSnapshot.Terms(request.amount(), request.deposit(),
                        request.paymentCycle().name(), request.startDate(), request.endDate(),
                        ChineseAmountConverter.toChinese(request.amount())),
                clauses);

        Contract entity = new Contract();
        entity.setHouseId(house.getId());
        entity.setCustomerId(customer.getId());
        entity.setOwnerId(house.getOwnerId());
        entity.setTitle(request.title().trim());
        entity.setAmount(request.amount());
        entity.setDeposit(request.deposit());
        entity.setPaymentCycle(request.paymentCycle().name());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(ContractStatus.DRAFT.name());
        entity.setTemplateVersion(templateVersion);
        entity.setSnapshotJson(toJson(snapshot));
        entity.setVersion(0);
        entity.setDeleted(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        insertWithRetry(entity);

        insertParties(entity.getId(), partySnapshots, now);
        writeStatusLog(entity.getId(), null, ContractStatus.DRAFT.name(), "起草合同");
        transactionService.initNodes(entity.getId(), now);

        // PDF 失败不影响草稿：仅记录日志，提交/生效前会强制重试
        tryGeneratePdf(entity, snapshot);
        log.info("合同创建 id={} no={} houseId={} customerId={}", entity.getId(), entity.getContractNo(),
                entity.getHouseId(), entity.getCustomerId());
        return buildDetail(contractMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional
    public ContractDetailVO update(Long id, ContractUpdateRequest request) {
        Contract existing = requireEntity(id);
        requireStatus(existing, ContractStatus.DRAFT);
        BigDecimal amount = request.amount() == null ? existing.getAmount() : request.amount();
        BigDecimal deposit = request.deposit() == null ? existing.getDeposit() : request.deposit();
        LocalDate startDate = request.startDate() == null ? existing.getStartDate() : request.startDate();
        LocalDate endDate = request.endDate() == null ? existing.getEndDate() : request.endDate();
        PaymentCycle cycle = request.paymentCycle() == null
                ? PaymentCycle.valueOf(existing.getPaymentCycle()) : request.paymentCycle();
        validateTerms(startDate, endDate, amount, deposit);

        ContractSnapshot snapshot = parseSnapshot(existing.getSnapshotJson());
        ContractSnapshot.Terms oldTerms = snapshot == null ? null : snapshot.terms();
        ContractSnapshot updatedSnapshot = new ContractSnapshot(
                snapshot == null ? existing.getTemplateVersion() : snapshot.templateVersion(),
                Instant.now(),
                snapshot == null ? null : snapshot.house(),
                snapshot == null ? List.of() : snapshot.parties(),
                new ContractSnapshot.Terms(amount, deposit, cycle.name(), startDate, endDate,
                        ChineseAmountConverter.toChinese(amount)),
                snapshot == null ? DEFAULT_CLAUSES : snapshot.clauses());

        Contract update = new Contract();
        update.setId(id);
        if (request.title() != null && !request.title().isBlank()) {
            update.setTitle(request.title().trim());
        }
        update.setAmount(amount);
        update.setDeposit(deposit);
        update.setPaymentCycle(cycle.name());
        update.setStartDate(startDate);
        update.setEndDate(endDate);
        update.setSnapshotJson(toJson(updatedSnapshot));
        update.setVersion(request.version());
        update.setUpdatedAt(Instant.now());
        int rows = contractMapper.updateById(update);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "合同已被他人修改，请刷新后重试");
        }
        Contract saved = contractMapper.selectById(id);
        // 金额或租期变化后重新生成 PDF（失败不阻塞草稿，旧 PDF 仍可查看但以最新快照为准）
        tryGeneratePdf(saved, updatedSnapshot);
        log.info("合同草稿更新 id={} amountChanged={}", id, oldTerms == null
                || oldTerms.amount() == null || oldTerms.amount().compareTo(amount) != 0);
        return buildDetail(contractMapper.selectById(id));
    }

    @Override
    @Transactional
    public ContractDetailVO submit(Long id) {
        Contract contract = requireEntity(id);
        requireStatus(contract, ContractStatus.DRAFT);
        if (contractMapper.countOccupyingByHouse(contract.getHouseId(), id) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "该房源已有待签署或生效中的合同");
        }
        ContractSnapshot snapshot = parseSnapshot(contract.getSnapshotJson());
        ensurePdfOrFail(contract, snapshot, "提交待签");
        int rows;
        try {
            rows = contractMapper.updateStatusIfMatch(id, ContractStatus.DRAFT.name(),
                    ContractStatus.PENDING_SIGN.name(), Instant.now());
        } catch (DuplicateKeyException e) {
            // 并发生成占用：唯一键 uk_contract_house_active 兜底
            throw new BusinessException(ErrorCode.DUPLICATE, "该房源已有待签署或生效中的合同");
        }
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "合同状态已变化，请刷新后重试");
        }
        writeStatusLog(id, ContractStatus.DRAFT.name(), ContractStatus.PENDING_SIGN.name(), "提交待签");
        log.info("合同提交待签 id={}", id);
        return buildDetail(contractMapper.selectById(id));
    }

    @Override
    @Transactional
    public ContractDetailVO confirm(Long id) {
        Contract contract = requireEntity(id);
        Long userId = currentUser.requireUserId();
        if (!userId.equals(contract.getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "合同不存在");
        }
        requireStatus(contract, ContractStatus.PENDING_SIGN);
        ContractSnapshot snapshot = parseSnapshot(contract.getSnapshotJson());
        String pdfKey = ensurePdfOrFail(contract, snapshot, "合同生效");

        // 行锁：同一房源的并发签约在此串行化，后到者看到最新状态后返回 409
        houseLifecycle.lock(contract.getHouseId());
        if (contractMapper.countOccupyingByHouse(contract.getHouseId(), id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该房源已存在待签署或生效中的合同");
        }
        Instant now = Instant.now();
        int rows;
        try {
            rows = contractMapper.activate(id, now, pdfKey);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "该房源已存在待签署或生效中的合同");
        }
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "合同已被他人处理，请刷新后重试");
        }
        houseLifecycle.markContracted(contract.getHouseId());
        transactionService.markSigned(id);
        writeStatusLog(id, ContractStatus.PENDING_SIGN.name(), ContractStatus.ACTIVE.name(), "客户确认签署");
        log.info("合同生效 id={} houseId={}", id, contract.getHouseId());
        return buildDetail(contractMapper.selectById(id));
    }

    @Override
    @Transactional
    public ContractDetailVO terminate(Long id, ContractTerminateRequest request) {
        Contract contract = requireEntity(id);
        ContractStatus current = ContractStatus.of(contract.getStatus());
        if (current == null || current.isTerminal()) {
            throw new BusinessException(ErrorCode.CONFLICT, "合同已结束，不能再次终止");
        }
        ContractStatus target = current == ContractStatus.DRAFT
                ? ContractStatus.CANCELLED : ContractStatus.TERMINATED;
        if (!current.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不允许终止合同");
        }
        String reason = request.reason().trim();
        int rows = contractMapper.updateStatusIfMatch(id, current.name(), target.name(), Instant.now());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "合同状态已变化，请刷新后重试");
        }
        writeStatusLog(id, current.name(), target.name(), reason);
        transactionService.cancelPending(id, reason);
        if (current == ContractStatus.ACTIVE
                && contractMapper.countOccupyingByHouse(contract.getHouseId(), id) == 0) {
            houseLifecycle.releaseContract(contract.getHouseId());
        }
        log.info("合同终止 id={} {} -> {} operator={}", id, current, target,
                currentUser.currentUserIdOrNull());
        return buildDetail(contractMapper.selectById(id));
    }

    @Override
    public ContractPdf downloadPdf(Long id) {
        Contract contract = requireEntity(id);
        requireVisible(contract);
        if (contract.getPdfUrl() == null || contract.getPdfUrl().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "合同 PDF 尚未生成");
        }
        byte[] content = privateStorage.read(contract.getPdfUrl());
        return new ContractPdf(content, contract.getContractNo() + ".pdf");
    }

    // ---------- 内部方法 ----------

    private PageResponse<ContractListItemVO> doList(ContractQuery query, Long forcedCustomerId) {
        ContractQuery safe = query == null ? new ContractQuery() : query;
        if (forcedCustomerId != null) {
            // 数据权限不接受客户端指定客户
            safe.setCustomerId(null);
        }
        safe.setStatus(normalizeStatus(safe.getStatus()));
        if (safe.getKeyword() != null) {
            String trimmed = safe.getKeyword().trim();
            if (trimmed.length() > 64) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "搜索关键字过长");
            }
            safe.setKeyword(trimmed.isEmpty() ? null : trimmed);
        }
        long total = contractMapper.countByQuery(safe, forcedCustomerId);
        long size = safe.normalizedSize();
        if (total == 0) {
            return PageResponse.of(List.of(), 0, safe.normalizedPage(), size);
        }
        List<ContractListItemVO> items = contractMapper
                .selectPageList(safe, forcedCustomerId, safe.offset(), size)
                .stream()
                .map(contractConvert::fillLabels)
                .toList();
        return PageResponse.of(items, total, safe.normalizedPage(), size);
    }

    private ContractDetailVO buildDetail(Contract contract) {
        ContractListItemVO listVo = contractMapper.selectVoById(contract.getId());
        ContractSnapshot snapshot = parseSnapshot(contract.getSnapshotJson());
        boolean admin = currentUser.isAdmin();
        List<ContractPartyVO> parties = partyMapper.selectList(Wrappers.<ContractParty>lambdaQuery()
                        .eq(ContractParty::getContractId, contract.getId())
                        .orderByAsc(ContractParty::getId))
                .stream()
                .map(p -> contractConvert.toPartyVO(p, admin))
                .toList();
        List<TransactionVO> transactions = transactionService.listByContract(contract.getId());
        List<ContractStatusLogVO> logs = statusLogMapper.selectList(
                        Wrappers.<ContractStatusLog>lambdaQuery()
                                .eq(ContractStatusLog::getContractId, contract.getId())
                                .orderByAsc(ContractStatusLog::getCreatedAt)
                                .orderByAsc(ContractStatusLog::getId))
                .stream()
                .map(contractConvert::toLogVO)
                .toList();
        return contractConvert.toDetail(contract, snapshot,
                listVo == null ? null : listVo.getCustomerName(),
                parties, transactions, logs);
    }

    private Contract requireEntity(Long id) {
        Contract contract = id == null ? null : contractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "合同不存在");
        }
        return contract;
    }

    /** CUSTOMER 访问他人合同返回 404，减少存在性泄露 */
    private void requireVisible(Contract contract) {
        if (currentUser.isAdmin()) {
            return;
        }
        Long userId = currentUser.requireUserId();
        if (!userId.equals(contract.getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "合同不存在");
        }
    }

    private void requireStatus(Contract contract, ContractStatus expected) {
        if (!expected.name().equals(contract.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "合同当前状态 " + contract.getStatus() + " 不允许该操作");
        }
    }

    private void writeStatusLog(Long contractId, String from, String to, String reason) {
        ContractStatusLog statusLog = new ContractStatusLog();
        statusLog.setContractId(contractId);
        statusLog.setFromStatus(from);
        statusLog.setToStatus(to);
        statusLog.setOperatorId(currentUser.currentUserIdOrNull());
        statusLog.setReason(reason);
        statusLog.setCreatedAt(Instant.now());
        statusLogMapper.insert(statusLog);
    }

    private void insertParties(Long contractId, List<ContractSnapshot.Party> parties, Instant now) {
        for (ContractSnapshot.Party party : parties) {
            ContractParty entity = new ContractParty();
            entity.setContractId(contractId);
            entity.setPartyType(party.type());
            entity.setName(party.name());
            entity.setPhone(party.phone());
            entity.setIdCardMasked(party.idCardMasked());
            entity.setCreatedAt(now);
            partyMapper.insert(entity);
        }
    }

    /** 合同编号冲突自动重试；房源占用冲突直接 409 */
    private void insertWithRetry(Contract entity) {
        for (int attempt = 0; attempt < 3; attempt++) {
            entity.setContractNo(nextContractNo());
            try {
                contractMapper.insert(entity);
                return;
            } catch (DuplicateKeyException e) {
                if (isHouseActiveConflict(e)) {
                    throw new BusinessException(ErrorCode.DUPLICATE, "该房源已有待签署或生效中的合同");
                }
                log.warn("合同编号冲突，重试 attempt={}", attempt + 1);
            }
        }
        throw new BusinessException(ErrorCode.UNEXPECTED, "合同编号生成冲突，请重试");
    }

    private boolean isHouseActiveConflict(DuplicateKeyException e) {
        String message = e.getMessage();
        return message != null && message.contains("uk_contract_house_active");
    }

    private String nextContractNo() {
        String date = LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        return "HT" + date + "-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    /** 客户资料未留电话时，回落到该客户对该房源最近一次预约留下的联系电话 */
    private String latestAppointmentPhone(Long houseId, Long customerId) {
        List<ViewingAppointment> appointments = appointmentMapper.selectList(
                Wrappers.<ViewingAppointment>lambdaQuery()
                        .eq(ViewingAppointment::getHouseId, houseId)
                        .eq(ViewingAppointment::getCustomerId, customerId)
                        .orderByDesc(ViewingAppointment::getId)
                        .last("LIMIT 1"));
        return appointments.isEmpty() ? null : appointments.get(0).getContactPhone();
    }

    private ContractTemplate activeTemplate() {
        List<ContractTemplate> templates = templateMapper.selectList(
                Wrappers.<ContractTemplate>lambdaQuery()
                        .eq(ContractTemplate::getCode, DEFAULT_TEMPLATE_CODE)
                        .eq(ContractTemplate::getActive, 1)
                        .orderByDesc(ContractTemplate::getId)
                        .last("LIMIT 1"));
        return templates.isEmpty() ? null : templates.get(0);
    }

    /** 生成 PDF 并落私有存储；失败抛业务异常（用于提交/生效前强制成功） */
    private String ensurePdfOrFail(Contract contract, ContractSnapshot snapshot, String action) {
        if (contract.getPdfUrl() != null && privateStorage.exists(contract.getPdfUrl())) {
            return contract.getPdfUrl();
        }
        try {
            String key = generateAndStore(contract, snapshot);
            contract.setPdfUrl(key);
            return key;
        } catch (RuntimeException e) {
            log.error("合同 PDF 生成失败，阻塞{} contractId={}", action, contract.getId(), e);
            throw new BusinessException(ErrorCode.UNEXPECTED, "合同 PDF 生成失败，无法" + action);
        }
    }

    /** 草稿阶段尽力生成：失败只记录日志，不阻塞保存 */
    private void tryGeneratePdf(Contract contract, ContractSnapshot snapshot) {
        if (contract.getPdfUrl() != null && privateStorage.exists(contract.getPdfUrl())) {
            return;
        }
        try {
            generateAndStore(contract, snapshot);
        } catch (RuntimeException e) {
            log.error("合同 PDF 生成失败（草稿可继续编辑） contractId={}", contract.getId(), e);
        }
    }

    private String generateAndStore(Contract contract, ContractSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalStateException("合同快照缺失，无法生成 PDF");
        }
        byte[] pdf = pdfGenerator.generate(snapshot, contract.getContractNo(), contract.getTitle());
        String key = privateStorage.storePdf(pdf);
        // V6.0.1 F5：仅对“本次新生成”的文件注册回滚清理；事务提交后保留，
        // 回滚（如并发占用冲突）时删除，避免 uploads-private 累积孤儿文件。
        registerRollbackCleanup(key);
        contractMapper.updatePdfUrl(contract.getId(), key, Instant.now());
        return key;
    }

    /** 事务上下文存在时注册回滚清理；无事务（如单测直接调用）保持原行为 */
    private void registerRollbackCleanup(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    try {
                        privateStorage.delete(key);
                    } catch (Exception e) {
                        log.warn("回滚清理合同 PDF 失败 key={}", key, e);
                    }
                }
            }
        });
    }

    private ContractSnapshot parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ContractSnapshot.class);
        } catch (JsonProcessingException e) {
            log.error("合同快照解析失败", e);
            return null;
        }
    }

    private String toJson(ContractSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.UNEXPECTED, "合同快照生成失败");
        }
    }

    private void validateTerms(LocalDate startDate, LocalDate endDate, BigDecimal amount, BigDecimal deposit) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "租期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "租期开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 365L * 20) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "租期不能超过 20 年");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "金额必须大于 0");
        }
        if (deposit == null || deposit.signum() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "押金不能为负数");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        ContractStatus parsed = ContractStatus.of(status);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "合同状态不合法");
        }
        return parsed.name();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
