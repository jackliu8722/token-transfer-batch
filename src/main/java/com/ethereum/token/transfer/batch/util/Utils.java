package com.ethereum.token.transfer.batch.util;

import com.ethereum.token.transfer.batch.ITimes;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Created by jackliu on 2018/4/8.
 */
public class Utils {

    public static void sleep(int seconds){
        try{
            Thread.sleep(seconds);
        }catch (InterruptedException e){

        }
    }

    public static BigDecimal valueTransfer(BigInteger value, int decimal){
        BigDecimal deposit ;
        BigDecimal d = new BigDecimal(value);
        deposit = d.divide(BigDecimal.valueOf(10).pow(decimal));
        return deposit;
    }

    public static <T> T times(ITimes <T>times,int retry){
        int t = 0;
        while(t++ < retry){
            T ret =  times.times();
            if(ret != null){
                return ret;
            }
        }
        return null;
    }
}
