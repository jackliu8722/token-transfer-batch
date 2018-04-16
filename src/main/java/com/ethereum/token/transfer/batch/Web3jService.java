package com.ethereum.token.transfer.batch;

import com.ethereum.token.transfer.batch.config.AppConfig;
import com.ethereum.token.transfer.batch.contract.TOKEN;
import com.ethereum.token.transfer.batch.util.Utils;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ClientTransactionManager;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

@Service
@CommonsLog
public class Web3jService {

    private Web3j web3j;

    @Autowired
    private AppConfig appConfig;

    private String tokenContractAddress;

    private String airDropContractAddress;

    private BigInteger nextBlockNumber;

    private Map<String,Integer> decimals = new HashMap<>();

    private TOKEN token;

    private Credentials credentials;

    private BigInteger nonce;

    private BigInteger gas;

    private BigInteger gasPrice;

    private int retryTimes;

    private TransactionManager transactionManager;

    TreeMap<String,BigInteger> balances = new TreeMap<>();

    private static final int SLEEP_DURATION = 5000;

    private static final int ATTEMPTS = 12 * 60 * 24;

    public void start() {

        this.tokenContractAddress = appConfig.getTokenContractAddress();
        this.airDropContractAddress = appConfig.getAirDropContractAddress();
        web3j = Web3j.build(new HttpService(appConfig.getGethHttpUrl()));
        gas = new BigInteger(appConfig.getGas());
        gasPrice = new BigInteger(appConfig.getGasPrice());
        log.info("init web3j success! ");
        try {
            credentials = WalletUtils.loadCredentials(appConfig.getWalletPassword(),
                    appConfig.getWalletFile());
        } catch (Exception e) {
            log.error("load wallet error",e);
            System.exit(1);
        }
        transactionManager = new RawTransactionManager(web3j,credentials);
        this.retryTimes = appConfig.getRetryTimes();
        token = createToken(tokenContractAddress);
        try {
            read(appConfig.getBalanceFilePath());
        } catch (Exception e) {
            log.error("read file error",e);
            System.exit(1);
        }

        if(appConfig.isOutputBalances()){
            balances.forEach((addr,value) -> {
                log.info("address = " + addr  + ", value = " +
                                Convert.fromWei(new BigDecimal(value),Convert.Unit.ETHER));
            });
            BigInteger total = balances.values().stream().reduce(BigInteger.ZERO,BigInteger::add);
            log.info("Address size = " + balances.size() +  ", Total balances : " + Convert.fromWei(new BigDecimal(total),Convert.Unit.ETHER));
        }

        if(appConfig.isBatch()) {
            nonce = getNonce();
            if (nonce == null) {
                log.error("can not get nonce");
                System.exit(1);
            }
            System.out.println(nonce);
            handle();
        }

        System.exit(0);
    }

    private void handle(){
        BigInteger total = balances.values().stream().reduce(BigInteger.ZERO,BigInteger::add);
        log.info("Total: " + Convert.fromWei(new BigDecimal(total),Convert.Unit.ETHER));
        String hash;
        if(appConfig.isApprove()){
            hash = approve(total);
            log.info("approve hash:" + hash);
            if(hash == null){
                return;
            }
            try {
                TransactionReceipt transactionReceipt = waitForTransactionReceipt(hash);
                if(transactionReceipt == null || !transactionReceipt.getStatus().equals("0x1")){
                    return;
                }
                nonce = nonce.add(BigInteger.ONE);
            } catch (Exception e) {
                log.error("",e);
            }
        }
        batch();
    }

    private void batch(){
        int length = appConfig.getLength();
        List<String> addresses = new ArrayList<>(balances.keySet());
        List<BigInteger> values = new ArrayList<>(balances.values());
        for(int i = appConfig.getStartIndex() ; i * length < balances.size() ; i++){
            List<String> addrs = new ArrayList<>();
            List<BigInteger> vals = new ArrayList<>();
            for(int j = i * length; j < (i + 1) * length && j < balances.size(); j++){
                addrs.add(addresses.get(j));
                vals.add(values.get(j));
            }

            String hash = batchSend(addrs,vals);
            log.info("Batch send for index = " + i + ", size = "+ addrs.size() + ", hash = " + hash);
            try {
                TransactionReceipt transactionReceipt = waitForTransactionReceipt(hash);
                if(transactionReceipt == null || !transactionReceipt.getStatus().equals("0x1")){
                    log.error("batch send hash is null");
                    return;
                }
                nonce = nonce.add(BigInteger.ONE);
            } catch (Exception e) {
                log.error("",e);
            }
        }
    }

    private String batchSend(List<String> addrs, List<BigInteger> vals){
        final Function function = new Function(
                "batch",
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(tokenContractAddress),
                        new org.web3j.abi.datatypes.Address(credentials.getAddress()),
                        new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(
                                org.web3j.abi.Utils.typeMap(addrs, org.web3j.abi.datatypes.Address.class)),
                        new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                                org.web3j.abi.Utils.typeMap(vals, org.web3j.abi.datatypes.generated.Uint256.class))),
                Collections.<TypeReference<?>>emptyList());
        String dataHex = FunctionEncoder.encode(function);

        RawTransaction rawTransaction = RawTransaction.createTransaction(nonce,gasPrice,gas,
                airDropContractAddress,dataHex);
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);
        try {
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();
            return ethSendTransaction.getTransactionHash();
        } catch (Exception e) {
            log.error("batch send error ", e);
        }
        return null;
    }

    private BigInteger getNonce(){
        String sender = credentials.getAddress();
        return Utils.times(()->{
            try {
                return web3j.ethGetTransactionCount(sender, DefaultBlockParameterName.LATEST).send().getTransactionCount();
            } catch (Exception e) {
                log.error("getNonce error",e);
            }
            return null;
        },retryTimes);
    }

    private String approve(BigInteger approveValue){
        Address address = new Address(airDropContractAddress);
        Uint256 value = new Uint256(approveValue);
        Function function = new Function("approve", Arrays.<Type>asList(address, value),
                Collections.<TypeReference<?>>emptyList());
        String dataHex = FunctionEncoder.encode(function);

        RawTransaction rawTransaction = RawTransaction.createTransaction(nonce,gasPrice,gas,
                tokenContractAddress,dataHex);
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);
        try {
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();
            return ethSendTransaction.getTransactionHash();
        } catch (Exception e) {
            log.error("Send approve transaction error ", e);
        }
        return null;
    }

    private void read(String path) throws Exception{
        FileInputStream inputStream = new FileInputStream(path);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        String str = null;
        while((str = bufferedReader.readLine()) != null)
        {

            String []arrays = str.split("\\s+");
            if(arrays.length !=2 ){
                continue;
            }
            String address = arrays[0].trim().toLowerCase();
            BigDecimal v = Convert.toWei(arrays[1],Convert.Unit.ETHER);
            BigInteger value = v.toBigInteger();
            if(balances.containsKey(address)){
                balances.put(address, balances.get(address).add(value));
            }else {
                balances.put(address,value);
            }
        }

        //close
        inputStream.close();
        bufferedReader.close();

    }

    private TOKEN createToken(String addr){
        TransactionManager manager = new ClientTransactionManager(web3j,addr);
        TOKEN token = TOKEN.load(
                addr, web3j, manager, new BigInteger(appConfig.getGasPrice()),
                new BigInteger(appConfig.getGas()));
        return token;
    }

    private TransactionReceipt waitForTransactionReceipt(
            String transactionHash) throws Exception {

        Optional<TransactionReceipt> transactionReceiptOptional =
                getTransactionReceipt(transactionHash, SLEEP_DURATION, ATTEMPTS);

        if (!transactionReceiptOptional.isPresent()) {
            log.warn("Transaction receipt not generated after " + ATTEMPTS + " attempts");
        }

        return transactionReceiptOptional.get();
    }

    private Optional<TransactionReceipt> getTransactionReceipt(
            String transactionHash, int sleepDuration, int attempts) throws Exception {

        Optional<TransactionReceipt> receiptOptional =
                sendTransactionReceiptRequest(transactionHash);
        for (int i = 0; i < attempts; i++) {
            if (!receiptOptional.isPresent()) {
                Thread.sleep(sleepDuration);
                receiptOptional = sendTransactionReceiptRequest(transactionHash);
            } else {
                break;
            }
        }

        return receiptOptional;
    }

    private Optional<TransactionReceipt> sendTransactionReceiptRequest(
            String transactionHash) throws Exception {
        EthGetTransactionReceipt transactionReceipt =
                web3j.ethGetTransactionReceipt(transactionHash).sendAsync().get();

        return transactionReceipt.getTransactionReceipt();
    }
}