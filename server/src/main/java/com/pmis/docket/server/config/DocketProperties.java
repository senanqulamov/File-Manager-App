package com.pmis.docket.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docket")
public class DocketProperties {
    /** Folder on disk where file contents are stored. */
    private String storageRoot = "./docket-data/storage";
    /** Sign-in lifetime in hours. */
    private int sessionHours = 12;
    /** Company display name. */
    private String companyName = "PMIS";
    /** Fill the database with demo users, folders and files on first start (local testing only). */
    private boolean seedDemoData = false;
    /** Days a deleted item stays in the Recycle Bin before IT can purge it. */
    private int recycleDays = 30;
    /** Password protecting the per-user signing keys on the server's disk. */
    private String keyPassword = "change-this-key-password";
    /** Full path to LibreOffice soffice(.exe). Empty = look in the usual places. */
    private String sofficePath = "";
    /** Full path to ffmpeg(.exe). Empty = use "ffmpeg" from PATH. */
    private String ffmpegPath = "";
    /** First administrator, created only when the database has no users at all (real server). */
    private String bootstrapAdminLogin = "";
    private String bootstrapAdminPassword = "";

    public String getStorageRoot() { return storageRoot; }
    public void setStorageRoot(String v) { this.storageRoot = v; }
    public int getSessionHours() { return sessionHours; }
    public void setSessionHours(int v) { this.sessionHours = v; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String v) { this.companyName = v; }
    public boolean isSeedDemoData() { return seedDemoData; }
    public void setSeedDemoData(boolean v) { this.seedDemoData = v; }
    public int getRecycleDays() { return recycleDays; }
    public void setRecycleDays(int v) { this.recycleDays = v; }
    public String getKeyPassword() { return keyPassword; }
    public void setKeyPassword(String v) { this.keyPassword = v; }
    public String getSofficePath() { return sofficePath; }
    public void setSofficePath(String v) { this.sofficePath = v; }
    public String getFfmpegPath() { return ffmpegPath; }
    public void setFfmpegPath(String v) { this.ffmpegPath = v; }
    public String getBootstrapAdminLogin() { return bootstrapAdminLogin; }
    public void setBootstrapAdminLogin(String v) { this.bootstrapAdminLogin = v; }
    public String getBootstrapAdminPassword() { return bootstrapAdminPassword; }
    public void setBootstrapAdminPassword(String v) { this.bootstrapAdminPassword = v; }
}
