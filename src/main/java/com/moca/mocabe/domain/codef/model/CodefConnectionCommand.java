package com.moca.mocabe.domain.codef.model;

/** CODEF Connected ID 생성 요청에 필요한 자격정보 값 객체다(평문, 요청 처리 중에만 사용). */
public class CodefConnectionCommand {

    private final String organization;
    private final String id;
    private final String password;
    private final String cardNo;
    private final String cardPassword;
    private final String birthDate;

    public CodefConnectionCommand(String organization, String id, String password,
                                  String cardNo, String cardPassword, String birthDate) {
        this.organization = organization;
        this.id = id;
        this.password = password;
        this.cardNo = cardNo;
        this.cardPassword = cardPassword;
        this.birthDate = birthDate;
    }

    public String getOrganization() {
        return organization;
    }

    public String getId() {
        return id;
    }

    public String getPassword() {
        return password;
    }

    public String getCardNo() {
        return cardNo;
    }

    public String getCardPassword() {
        return cardPassword;
    }

    public String getBirthDate() {
        return birthDate;
    }
}
