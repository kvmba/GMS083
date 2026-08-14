/*
    This file is part of the BeiDou MapleStory Server
 */
package org.gms.config;

import lombok.extern.slf4j.Slf4j;
import org.gms.util.BCrypt;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动预热:应用就绪后跑一次 BCrypt 校验(与登录同成本 cost=12),
 * 提前完成 blowfish 循环的类加载与 JIT 编译,消除首次登录的冷启动耗时尖峰(此前出现过 16 秒)。
 */
@Slf4j
@Component
public class StartupWarmup {

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            String warmupHash = BCrypt.hashpw("warmup", BCrypt.gensalt(12));
            if (BCrypt.checkpw("warmup", warmupHash)) {
                log.info("启动预热:BCrypt 就绪");
            }
        } catch (Exception e) {
            log.warn("启动预热:BCrypt 自检失败({})", e.getMessage());
        }
    }
}
