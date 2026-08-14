/*
    This file is part of the BeiDou MapleStory Server
 */
package org.gms.config;

import lombok.extern.slf4j.Slf4j;
import org.gms.util.BCrypt;
import org.gms.util.DatabaseConnection;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 启动预热:应用就绪后创建一条数据库连接,并跑一次 BCrypt 校验(与登录同成本 cost=12),
 * 消除首次登录的冷启动耗时尖峰(此前出现过 16 秒的登录耗时)。
 */
@Slf4j
@Component
public class StartupWarmup {

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try (Connection con = DatabaseConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            if (rs.next()) {
                log.info("启动预热:数据库连接就绪");
            }
        } catch (SQLException e) {
            log.warn("启动预热:数据库连接失败({})", e.getMessage());
        }

        try {
            String warmupHash = BCrypt.hashpw("warmup", BCrypt.gensalt(12));
            // 跑两次:第一次完成类加载,第二次触发 JIT C1 编译
            BCrypt.checkpw("warmup", warmupHash);
            if (BCrypt.checkpw("warmup", warmupHash)) {
                log.info("启动预热:BCrypt 就绪");
            }
        } catch (Exception e) {
            log.warn("启动预热:BCrypt 自检失败({})", e.getMessage());
        }
    }
}
