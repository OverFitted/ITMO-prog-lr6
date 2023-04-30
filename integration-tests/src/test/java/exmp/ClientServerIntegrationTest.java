package exmp;

import exmp.commands.CommandResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClientServerIntegrationTest {
    private static final int SERVER_PORT = 52333;
    private static final String HOST = "localhost";
    private static exmp.Server server;
    private static final exmp.App app = new exmp.App("src/test/resources/input.xml");

    private static final Logger logger = LogManager.getLogger(ClientServerIntegrationTest.class);

    @BeforeAll
    public static void setup() {
        logger.info("Starting tests...");

        server = new exmp.Server(SERVER_PORT, app);
        new Thread(() -> server.start()).start();
    }

    @AfterAll
    public static void join() {
        logger.info("All tests finished");
    }

    @Test
    public void testSuccessfulCommunication() throws IOException, ClassNotFoundException {
        logger.debug("Testing client-server communication...");

        String commandName = "help";
        String commandInput = "";

        CommandResult expectedResult = app.executeCommand(commandName, commandInput);
        CommandResult result = sendCommand(commandName, commandInput);

        assertEquals(expectedResult.getStatusCode(), result.getStatusCode());
        assertEquals(expectedResult.getOutput(), result.getOutput());
    }

    @Test
    public void testPacketLoss() throws IOException, ClassNotFoundException {
        logger.debug("Testing packet loss...");

        String commandName = "clear";
        String commandInput = "";

        CommandResult expectedResult = app.executeCommand(commandName, commandInput);

        // Вероятность потери пакета (от 0 до 1, где 0 - нет потерь, 1 - все пакеты потеряны)
        double packetLossProbability = 0.3;
        Random random = new Random();

        CommandResult result = null;
        for (int i = 0; i < 5; i++) {
            if (random.nextDouble() >= packetLossProbability) {
                result = sendCommand(commandName, commandInput);
            }

            if (result != null && result.getStatusCode() == expectedResult.getStatusCode()) {
                break;
            }
        }

        assertNotNull(result, "Не удалось получить результат из-за потери пакетов");
        assertEquals(expectedResult.getStatusCode(), result.getStatusCode());
        assertEquals(expectedResult.getOutput(), result.getOutput());
    }

    @Test
    public void testPacketDuplication() throws IOException, ClassNotFoundException {
        logger.debug("Testing packet duplication...");

        String commandName = "help";
        String commandInput = "";

        sendCommand(commandName, commandInput);
        CommandResult result = sendCommand(commandName, commandInput);

        CommandResult expectedResult = app.executeCommand(commandName, commandInput);

        assertEquals(expectedResult.getStatusCode(), result.getStatusCode());
        assertEquals(expectedResult.getOutput(), result.getOutput());
    }

    @Test
    public void testPacketReordering() throws IOException, ClassNotFoundException {
        logger.debug("Testing packet reordering...");

        String commandName1 = "info";
        String commandInput1 = "";

        String commandName2 = "help";
        String commandInput2 = "";

        CommandResult result2 = sendCommand(commandName1, commandInput1);
        CommandResult result1 = sendCommand(commandName2, commandInput2);

        CommandResult expectedResult1 = app.executeCommand(commandName1, commandInput1);
        CommandResult expectedResult2 = app.executeCommand(commandName2, commandInput2);

        assertNotEquals(expectedResult1.getOutput(), result1.getOutput());
        assertNotEquals(expectedResult2.getOutput(), result2.getOutput());
    }

    @Test
    public void testCommandExecError() throws  IOException, ClassNotFoundException{
        logger.debug("Testing command execution with wrong args...");

        String commandName = "show";
        String commandInput = "1 2 3 4 5";

        sendCommand(commandName, commandInput);
        CommandResult result = sendCommand(commandName, commandInput);

        String commandName2 = "filter_by_unit_of_measure";
        String commandInput2 = "1 2 3 4 5";

        sendCommand(commandName2, commandInput2);
        CommandResult result2 = sendCommand(commandName2, commandInput2);

        assertNotEquals(0, result.getStatusCode());
        assertNotEquals(0, result2.getStatusCode());
    }

    @Test
    public void testCommandNotExist() throws  IOException, ClassNotFoundException{
        logger.debug("Testing not existing command...");

        CommandResult result = app.executeCommand("fake", "command");

        String commandName = "fake";
        String commandInput = "command";

        sendCommand(commandName, commandInput);
        CommandResult result2 = sendCommand(commandName, commandInput);

        assertNotEquals(0, result.getStatusCode());
        assertNotEquals(0, result2.getStatusCode());
    }

    @Test
    public void testProductAddError() throws  IOException, ClassNotFoundException{
        logger.debug("Testing product adding with wrong args...");

        String commandName = "add";
        String commandInput = "1 2 3";

        sendCommand(commandName, commandInput);
        CommandResult result = sendCommand(commandName, commandInput);

        assertNotEquals(0, result.getStatusCode());
    }

    @Test
    public void testProductCount() throws  IOException, ClassNotFoundException{
        logger.debug("Testing product count...");
        Pattern pattern = Pattern.compile("\\d+");

        int currentProductsCountNaive = app.getProductRepository().findAll().size();

        Matcher matcherClient = pattern.matcher(app.executeCommand("info", "").getOutput());
        int currentProductsCountClient = 0;
        while (matcherClient.find()) {
            currentProductsCountClient = Integer.parseInt(matcherClient.group());
        }

        Matcher matcherServer = pattern.matcher(sendCommand("info", "").getOutput());
        int currentProductsCountServer = 0;
        while (matcherServer.find()) {
            currentProductsCountServer = Integer.parseInt(matcherServer.group());
        }

        assertEquals(currentProductsCountNaive, currentProductsCountClient);
        assertEquals(currentProductsCountClient, currentProductsCountServer);
    }

    private CommandResult sendCommand(String commandName, String commandInput) throws IOException, ClassNotFoundException {
        try (DatagramSocket datagramSocket = new DatagramSocket()) {
            exmp.commands.CommandData outCommand = new exmp.commands.CommandData(commandName, commandInput);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(outCommand);
            byte[] data = byteArrayOutputStream.toByteArray();

            DatagramPacket sendPacket = new DatagramPacket(data, data.length, new InetSocketAddress(HOST, SERVER_PORT));
            datagramSocket.send(sendPacket);

            ByteBuffer buffer = ByteBuffer.allocate(65536);
            DatagramPacket receivePacket = new DatagramPacket(buffer.array(), buffer.capacity());
            datagramSocket.receive(receivePacket);
            buffer.position(receivePacket.getLength());

            buffer.flip();
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buffer.array(), 0, buffer.limit());
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);

            return (CommandResult) objectInputStream.readObject();
        }
    }
}
