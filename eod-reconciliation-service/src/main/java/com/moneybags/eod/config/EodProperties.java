package com.moneybags.eod.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties("moneybags.eod")
public class EodProperties {
    private LocalDate initialBusinessDate = LocalDate.now();
    private String persistence = "memory";
    private boolean stubPeerClients = true;
    private Set<String> stubFailOn = new LinkedHashSet<>();

    public LocalDate getInitialBusinessDate() { return initialBusinessDate; }
    public void setInitialBusinessDate(LocalDate initialBusinessDate) { this.initialBusinessDate = initialBusinessDate; }
    public String getPersistence() { return persistence; }
    public void setPersistence(String persistence) { this.persistence = persistence; }
    public boolean isStubPeerClients() { return stubPeerClients; }
    public void setStubPeerClients(boolean stubPeerClients) { this.stubPeerClients = stubPeerClients; }
    public Set<String> getStubFailOn() { return stubFailOn; }
    public void setStubFailOn(Set<String> stubFailOn) { this.stubFailOn = stubFailOn == null ? new LinkedHashSet<>() : stubFailOn; }
}
