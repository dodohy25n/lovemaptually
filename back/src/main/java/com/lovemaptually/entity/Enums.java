package com.lovemaptually.entity;

public final class Enums {
    private Enums() {}
    public enum GroupType { COUPLE, FAMILY, FRIENDS }
    public enum MemberRole { OWNER, MEMBER }
    public enum InviteStatus { ACTIVE, EXPIRED, REVOKED }
    public enum AttrLevel { HIGH, LOW }
    public enum TagAxis { ATMOSPHERE, TASTE, CONVENIENCE, PRICE, PURPOSE }
    public enum RecommendationStatus { PENDING, COMPLETED, FAILED }
    public enum RecommendationBasis { OWN, OTHERS }
    public enum PlaceLabel { ALL_LIKED, MIXED, ON_HOLD }
    public enum TagStatus { PENDING, COMPLETED, FAILED }
}
