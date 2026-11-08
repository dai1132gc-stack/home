package com.example.realestate.modules.appointment.convert;

import com.example.realestate.common.enums.AppointmentStatus;
import com.example.realestate.common.util.PhoneMasker;
import com.example.realestate.modules.appointment.model.vo.AppointmentVO;
import org.springframework.stereotype.Component;

/**
 * 预约 VO 组装：按角色裁剪客户信息与联系电话（ADMIN 看到完整号码用于联系客户）。
 */
@Component
public class AppointmentConvert {

    public AppointmentVO forViewer(AppointmentVO source, boolean admin) {
        if (source == null) {
            return null;
        }
        source.setStatusLabel(label(source.getStatus()));
        if (!admin) {
            source.setContactPhone(PhoneMasker.mask(source.getContactPhone()));
            source.setCustomerName(null);
        }
        return source;
    }

    public String label(String status) {
        AppointmentStatus parsed = AppointmentStatus.of(status);
        return parsed == null ? status : parsed.label();
    }
}
