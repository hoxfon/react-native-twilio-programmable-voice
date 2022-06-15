package com.hoxfon.react.RNTwilioVoice.models;

public class Visitor {

  Visitor() {}

  public String getVisitorName() {
    return visitorName;
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

  public String getUnit() {
    return unit;
  }

  private String visitorName;

  private String visitorAvatarUri;

  private String callSid;

  private String providerType;

  private String relationship;

  private String unit;
}