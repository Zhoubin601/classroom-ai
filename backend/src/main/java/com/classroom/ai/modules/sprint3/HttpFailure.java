package com.classroom.ai.modules.sprint3;
public class HttpFailure extends RuntimeException {
 public final int status;
 public HttpFailure(int status, String message) { super(message); this.status=status; }
}

