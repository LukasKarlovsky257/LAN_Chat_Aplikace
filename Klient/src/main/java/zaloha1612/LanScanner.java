package zaloha1612;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LanScanner {
    public interface ScanCallback { void onServerFound(String ip); void onScanFinished(); }
    public static void scan(int port, ScanCallback cb) {
        new Thread(()->{
            ExecutorService es = Executors.newFixedThreadPool(20);
            try {
                String sub = java.net.InetAddress.getLocalHost().getHostAddress();
                sub = sub.substring(0, sub.lastIndexOf('.')+1);
                for(int i=1;i<255;i++) { String ip=sub+i; es.execute(()->{ try(Socket s=new Socket()){ s.connect(new InetSocketAddress(ip,port),200); cb.onServerFound(ip); }catch(Exception e){} }); }
                es.shutdown(); es.awaitTermination(5, TimeUnit.SECONDS);
            } catch(Exception e){}
            cb.onScanFinished();
        }).start();
    }
}