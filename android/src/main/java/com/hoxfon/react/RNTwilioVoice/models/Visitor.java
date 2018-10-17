package com.hoxfon.react.RNTwilioVoice.models;

public class Visitor {

  Visitor() {}

  public Long getId() {
    return id;
  }

  public String getInvitation() {
    return invitation;
  }

  public String getVisitorFullName() {
    return visitorFullName;
  }

  public String getVisitorAvatarUri() {
    return visitorAvatarUri;
  }

  public String getCallSid() {
    return callSid;
  }

  public String getProviderType() {
    return providerType;
  }

  public String getRelationship() {
    return relationship;
  }

  private Long id;

  private String invitation;

  private String visitorFullName;

  private String visitorAvatarUri;

  private String callSid;

  private String providerType;

  private String relationship;

}