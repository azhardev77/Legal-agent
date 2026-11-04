package com.tenderops.web;

public class EmailTakenException extends RuntimeException {
  public EmailTakenException() {
    super("Email is already registered");
  }
}
