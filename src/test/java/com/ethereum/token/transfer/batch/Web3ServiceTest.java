package com.ethereum.token.transfer.batch;

import org.junit.Test;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Created by jackliu on 2018/4/9.
 */
public class Web3ServiceTest {

    @Test
    public void test(){
        List<BigInteger> list = new ArrayList<BigInteger>();
        list.add(BigInteger.valueOf(1));
        list.add(BigInteger.valueOf(2));
        list.add(BigInteger.valueOf(3));
        list.add(BigInteger.valueOf(4));

        BigInteger total = list.stream().reduce(BigInteger.ZERO,BigInteger::add);
        System.out.println(total);
    }

    @Test
    public void testRead() throws IOException {
        FileInputStream inputStream = new FileInputStream("/Users/jackliu/workspace/shulian/tg/token-tranfer-batch/addr.txt");
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        String str = null;
        while((str = bufferedReader.readLine()) != null)
        {

            System.out.println(str);
            String []arrays = str.split("\\s+");
            if(arrays.length !=2 ){
                continue;
            }

            String address = arrays[0].toLowerCase();
            BigInteger value = new BigInteger(arrays[1]);
            System.out.println(address + "," + value);
        }

        //close
        inputStream.close();
        bufferedReader.close();
    }

    @Test
    public void testMap(){
        TreeMap<String,Integer> map = new TreeMap<>();
        map.put("3",1);
        map.put("2",2);
        map.put("1",3);
        map.put("4",4);

        System.out.println(map.keySet());
        System.out.println(map.values());
    }
}
