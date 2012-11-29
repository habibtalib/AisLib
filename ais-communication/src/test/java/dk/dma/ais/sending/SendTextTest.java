package dk.dma.ais.sending;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import dk.dma.ais.message.AisMessage12;
import dk.dma.ais.reader.AisReader;
import dk.dma.ais.reader.AisTcpReader;
import dk.dma.ais.reader.ClientSendThread;
import dk.dma.ais.reader.SendException;
import dk.dma.ais.reader.SendRequest;
import dk.dma.ais.reader.AisReader.Status;
import dk.dma.ais.sentence.Abk;
import dk.dma.ais.sentence.AnySentence;

@Ignore
public class SendTextTest {

    // @Test
    public void sendAddressedTextTest1() throws InterruptedException, SendException {
        // String hostPort = "aistrans1:4001";
        String hostPort = "localhost:4001";
        String message = "TEST FROM FRV";
        int destination = 219015063; // DAMSA1
        // int destination = 219593000; // Jens S??rensen
        // int destination = 219997000; // Poul L??wen??rn
        sendAddressedTextSimple(hostPort, destination, message);
        sleep(2000);
    }

    // @Test
    public void sendAddressedTextTest2() throws InterruptedException, SendException {
        String hostPort = "aistrans1:4001";
        String message = "TEST FROM FRV";
        int destination = 219593000;
        sendAddressedTextSimple(hostPort, destination, message);
        sleep(2000);
    }

    @Test
    public void sendAddressedTextTest3() throws InterruptedException, SendException {
        String hostPort = "aistrans1.fomfrv.dk:4001";
        String message = "TEST FROM DAMSA1";
        int destination = 992199001; // SOK
        // int destination = 992199007; // ais-frv-obo
        sendAddressedTextSimple(hostPort, destination, message);
        sleep(2000);
    }

    // @Test
    public void sendAddressedTextTest4() throws InterruptedException, SendException {
        // String hostPort = "aistrans1:4001";
        String hostPort = "localhost:4001";
        String message = "TEST FROM FRV";
        int destination = 219997000;
        sendAddressedTextSimple(hostPort, destination, message);
        sleep(2000);
    }

    // @Test
    public void sendWithPrefixSentence() throws SendException, InterruptedException {
        AnySentence sentence = new AnySentence();
        sentence.setDelimiter("$");
        sentence.setTalker("PGHP");
        sentence.setFormatter("");
        sentence.addField("23");
        sentence.addField("992199110");
        String virtMmsiSentence = sentence.getEncoded();

        List<String> prefixSentences = new ArrayList<>();
        prefixSentences.add(virtMmsiSentence);

        String hostPort = "localhost:4001";
        // String message = "TEST FROM FRV";
        String message = "A";
        // int destination = 219015063; // DAMSA1
        // int destination = 219593000; // Jens S??rensen
        // int destination = 219997000; // Poul L??wen??rn
        int destination = 123456789;
        sendAddressedWithPrefix(hostPort, destination, message, prefixSentences);
        sleep(2000);

    }

    private void sendAddressedWithPrefix(String hostPort, int destination, String message, List<String> prefixSentences)
            throws InterruptedException, SendException {
        // Make AisReader instance and start
        AisReader aisReader = new AisTcpReader(hostPort);
        aisReader.start();
        Thread.sleep(3000);
        if (aisReader.getStatus() != Status.CONNECTED) {
            Assert.fail("Could not connect to AIS source within 3 secs");
        }

        // Make AIS message 12
        AisMessage12 msg12 = new AisMessage12();
        msg12.setDestination(destination);
        msg12.setMessage(message);

        // Make sendrequest
        SendRequest sendRequest = new SendRequest(msg12, 2, destination);
        sendRequest.setTalker("PN");
        for (String prefixSentence : prefixSentences) {
            sendRequest.addPrefixSentence(prefixSentence);
        }

        // Send
        ClientSendThread clientSendThread = new ClientSendThread(aisReader, sendRequest);
        Abk abk = clientSendThread.send();

        // We are now guaranteed to have ABK
        System.out.println("ABK: " + abk);
        Assert.assertTrue(abk.isSuccess());
        aisReader.close();
    }

    private void sendAddressedTextSimple(String hostPort, int destination, String message) throws InterruptedException,
            SendException {
        // Make AisReader instance and start
        AisReader aisReader = new AisTcpReader(hostPort);
        aisReader.start();
        Thread.sleep(3000);
        if (aisReader.getStatus() != Status.CONNECTED) {
            Assert.fail("Could not connect to AIS source within 3 secs");
        }

        // Make AIS message 12
        AisMessage12 msg12 = new AisMessage12();
        msg12.setDestination(destination);
        msg12.setMessage(message);

        // Send
        Abk abk = aisReader.send(msg12, 1, destination);

        // We are now guaranteed to have ABK
        System.out.println("ABK: " + abk);
        Assert.assertTrue(abk.isSuccess());
        aisReader.close();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
