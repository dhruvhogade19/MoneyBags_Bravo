package com.moneybags.payments.exception;

public class PeerServiceException extends RuntimeException {
  private final String service;
  private final int status;
  private final String code;

  public PeerServiceException(String service, int status, String code, String message) {
    super(message);
    this.service = service;
    this.status = status;
    this.code = code;
  }

  public String getService() { return service; }
  public int getStatus() { return status; }
  public String getCode() { return code; }
}
