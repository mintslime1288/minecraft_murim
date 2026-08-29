package com.murimrpg.domain;

public enum Constitution {
    CHEONMU("cheonmu", "천무지체", "모든 무공 숙련도 획득량이 3배 증가합니다."),
    MUGEUK("mugeuk", "무극지체", "비운기조식 상태에서도 내공을 회복하며 심법 숙련도가 증가합니다."),
    GONGRYEONG("gongryeong", "공령지체", "내공이 부족해도 무공을 사용할 수 있지만 성능이 75%가 됩니다."),
    GUYANG("guyang", "구양절맥", "운기조식 또는 무공 사용 시 최대 내공과 체력을 잃을 수 있습니다."),
    GUUM("guum", "구음절맥", "운기조식 또는 무공 사용 시 최대 내공과 체력을 잃을 수 있습니다."),
    MUGOL("mugol", "무골", "모든 무공 숙련도 획득량이 1.25배 증가합니다."),
    ORDINARY("ordinary", "일반인", "특별한 체질 효과가 없습니다.");
    private final String id, displayName, description;
    Constitution(String id, String displayName, String description) { this.id=id; this.displayName=displayName; this.description=description; }
    public String id(){ return id; } public String displayName(){ return displayName; } public String description(){ return description; }
    public static Constitution fromId(String id) { for (Constitution value:values()) if(value.id.equalsIgnoreCase(id)) return value; throw new IllegalArgumentException("Unknown constitution: "+id); }
}
