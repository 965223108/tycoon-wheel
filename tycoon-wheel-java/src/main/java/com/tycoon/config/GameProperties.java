package com.tycoon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对应 application.yml 里的 game.* —— 改配置即改玩法，不用动代码。
 */
@ConfigurationProperties(prefix = "game")
public class GameProperties {

    private int costPerSpin = 10;
    private int initialPoints = 100;
    private int dailyLimit = 0;
    private int sessionTtlHours = 168;
    private int historyKeep = 60;
    private int maxSpinsPerMinute = 30;

    public int getCostPerSpin() { return costPerSpin; }
    public void setCostPerSpin(int v) { this.costPerSpin = v; }

    public int getInitialPoints() { return initialPoints; }
    public void setInitialPoints(int v) { this.initialPoints = v; }

    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int v) { this.dailyLimit = v; }

    public int getSessionTtlHours() { return sessionTtlHours; }
    public void setSessionTtlHours(int v) { this.sessionTtlHours = v; }

    public int getHistoryKeep() { return historyKeep; }
    public void setHistoryKeep(int v) { this.historyKeep = v; }

    public int getMaxSpinsPerMinute() { return maxSpinsPerMinute; }
    public void setMaxSpinsPerMinute(int v) { this.maxSpinsPerMinute = v; }
}
