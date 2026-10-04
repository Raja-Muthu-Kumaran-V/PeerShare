package peershare;

import javax.crypto.SecretKey;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Reproducible micro-benchmark for the figures quoted in the PeerShare report.
 * Run from the project root (after `mvn compile`):
 *   java -cp target/classes peershare.CryptoBenchmark
 * Reports median / min / max over several runs after a JIT warm-up.
 */
public class CryptoBenchmark {
    static final int SIZE = 10 * 1024 * 1024;   // 10 MB
    static final int WARMUP = 5, RUNS = 20, RSA_RUNS = 30;

    interface Task { void run() throws Exception; }

    static double[] time(int warm, int runs, Task t) throws Exception {
        for (int i = 0; i < warm; i++) t.run();
        double[] ms = new double[runs];
        for (int i = 0; i < runs; i++) {
            long s = System.nanoTime();
            t.run();
            ms[i] = (System.nanoTime() - s) / 1e6;
        }
        Arrays.sort(ms);
        return ms;
    }
    static void report(String name, double[] ms, boolean throughput) {
        double med = ms[ms.length / 2];
        String tp = throughput ? String.format("  (~%.0f MB/s at median)", 10.0 / (med / 1000)) : "";
        System.out.printf("%-34s median %8.1f ms | min %8.1f | max %8.1f | n=%d%s%n",
                name, med, ms[0], ms[ms.length - 1], ms.length, tp);
    }

    public static void main(String[] a) throws Exception {
        System.out.println("JDK " + System.getProperty("java.version") + " | " + System.getProperty("os.name")
                + " | " + Runtime.getRuntime().availableProcessors() + " cores | arch " + System.getProperty("os.arch"));
        try { for (String l : java.nio.file.Files.readAllLines(java.nio.file.Path.of("/proc/cpuinfo")))
                if (l.startsWith("model name")) { System.out.println("CPU " + l.split(":")[1].trim()); break; }
        } catch (Exception ignored) { }

        byte[] data = new byte[SIZE];
        new SecureRandom().nextBytes(data);
        SecretKey key = CryptoUtil.generateAESKey();
        byte[] iv = CryptoUtil.generateIV();
        

        report("RSA-2048 keypair generation", time(3, RSA_RUNS, CryptoUtil::generateRSAKeyPair), false);
        report("AES-128/CBC encrypt 10 MB", time(WARMUP, RUNS, () -> {
            try (OutputStream o = CryptoUtil.encryptStream(OutputStream.nullOutputStream(), key, iv)) { o.write(data); }
        }), true);
        report("SHA-256 digest 10 MB", time(WARMUP, RUNS, () ->
                MessageDigest.getInstance("SHA-256").digest(data)), true);
    }
}
