package com.ethereum.token.transfer.batch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "web")
public class AppConfig {


    private String gethHttpUrl;

    private String tokenContractAddress;

    private String destAddress;

    private String privateKey;

    private int retryTimes;

    private String gas;

    private String gasPrice;

    private String balanceFilePath;

    private String walletFile;

    private String walletPassword;

    private boolean approve;

    private String airDropContractAddress;


    private int startIndex;

    private int length;

    private boolean batch;

    private boolean outputBalances;

}
