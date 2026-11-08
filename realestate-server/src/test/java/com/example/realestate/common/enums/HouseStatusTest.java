package com.example.realestate.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("房源状态机")
class HouseStatusTest {

    @Test
    @DisplayName("PENDING 只能发布，不能直接下架")
    void pendingCanOnlyPublish() {
        assertThat(HouseStatus.PENDING.canTransitionTo(HouseStatus.PUBLISHED)).isTrue();
        assertThat(HouseStatus.PENDING.canTransitionTo(HouseStatus.OFFLINE)).isFalse();
        assertThat(HouseStatus.PENDING.canTransitionTo(HouseStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("PUBLISHED 可下架或进入预定/签约，不能回到 PENDING")
    void publishedCanOnlyOffline() {
        assertThat(HouseStatus.PUBLISHED.canTransitionTo(HouseStatus.OFFLINE)).isTrue();
        assertThat(HouseStatus.PUBLISHED.canTransitionTo(HouseStatus.RESERVED)).isTrue();
        assertThat(HouseStatus.PUBLISHED.canTransitionTo(HouseStatus.CONTRACTED)).isTrue();
        assertThat(HouseStatus.PUBLISHED.canTransitionTo(HouseStatus.PUBLISHED)).isFalse();
        assertThat(HouseStatus.PUBLISHED.canTransitionTo(HouseStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("RESERVED 可回到 PUBLISHED 或签约，CONTRACTED 终止后回到 PUBLISHED")
    void reservedAndContractedTransitions() {
        assertThat(HouseStatus.RESERVED.canTransitionTo(HouseStatus.PUBLISHED)).isTrue();
        assertThat(HouseStatus.RESERVED.canTransitionTo(HouseStatus.CONTRACTED)).isTrue();
        assertThat(HouseStatus.RESERVED.canTransitionTo(HouseStatus.OFFLINE)).isFalse();
        assertThat(HouseStatus.CONTRACTED.canTransitionTo(HouseStatus.PUBLISHED)).isTrue();
        assertThat(HouseStatus.CONTRACTED.canTransitionTo(HouseStatus.RESERVED)).isFalse();
        assertThat(HouseStatus.CONTRACTED.canTransitionTo(HouseStatus.OFFLINE)).isFalse();
    }

    @Test
    @DisplayName("只有 PUBLISHED 对外可见")
    void onlyPublishedIsPublic() {
        assertThat(HouseStatus.PUBLISHED.isPubliclyVisible()).isTrue();
        assertThat(HouseStatus.RESERVED.isPubliclyVisible()).isFalse();
        assertThat(HouseStatus.CONTRACTED.isPubliclyVisible()).isFalse();
        assertThat(HouseStatus.OFFLINE.isPubliclyVisible()).isFalse();
    }

    @Test
    @DisplayName("OFFLINE 可重新发布")
    void offlineCanRepublish() {
        assertThat(HouseStatus.OFFLINE.canTransitionTo(HouseStatus.PUBLISHED)).isTrue();
        assertThat(HouseStatus.OFFLINE.canTransitionTo(HouseStatus.OFFLINE)).isFalse();
    }

    @Test
    @DisplayName("非法字符串解析为 null，大小写不敏感")
    void ofHandlesInvalidAndCase() {
        assertThat(HouseStatus.of("published")).isEqualTo(HouseStatus.PUBLISHED);
        assertThat(HouseStatus.of("DELETED")).isNull();
        assertThat(HouseStatus.of(null)).isNull();
        assertThat(HouseStatus.of(" ")).isNull();
    }
}
