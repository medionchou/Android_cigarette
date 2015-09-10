package com.medion.project_cigarette;

import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by Medion on 2015/9/4.
 */
public class Client extends Thread {

    private final String SERVER_IP = "140.113.167.14";
    private final int SERVER_PORT = 9000;

    private SocketChannel socketChannel;
    private ByteBuffer inputBuffer;
    private CharBuffer outStream;
    private String serverReply;
    private String cmd;
    private String queryReply;
    private String updateMsg;
    private String msg;
    private List<Byte> buffer;
    private MainActivity mainActivity;

    private boolean isTerminated;
    private int client_state;

    public Client(MainActivity mainActivity) {
        initObject();
        this.mainActivity = mainActivity;
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        setUpConnection();
    }

    private void initObject() {
        isTerminated = false;
        inputBuffer = ByteBuffer.allocate(2048);
        client_state = States.CONNECT_INITIALZING;
        serverReply = "";
        cmd = "";
        queryReply = "";
        updateMsg = "";
        msg = "";
        buffer = new ArrayList<>();
        inputBuffer.clear();
    }


    private void setUpConnection() {

        try {
            while (!isTerminated) {

                if (socketChannel == null) {

                    socketChannel = SocketChannel.open();
                    socketChannel.configureBlocking(false);
                    socketChannel.connect(new InetSocketAddress(SERVER_IP, SERVER_PORT));

                    while (!socketChannel.finishConnect()) {
                        Thread.sleep(2000);
                    }

                } else if (socketChannel != null) {

                    int num;
                    while ((num = socketChannel.read(inputBuffer)) > 0) {
                        inputBuffer.flip();
                        while (inputBuffer.hasRemaining()) {
                            buffer.add(inputBuffer.get());
                        }
                        inputBuffer.clear();
                    }

                    if (num < 0)
                        throw new IOException("Server disconnect");

                    if (buffer.size() > 0) {

                        if (buffer.get(buffer.size() - 1) > 0) {
                            byte[] tmp = new byte[buffer.size()];
                            for (int i = 0; i < buffer.size(); i++)
                                tmp[i] = buffer.get(i);
                            serverReply += new String(tmp, "UTF-8");
                            buffer.clear();
                        }
                    }

                    while (serverReply.contains("<END>")) {

                        int endIndex = serverReply.indexOf("<END>") + 5;
                        if (endIndex < 0) endIndex = 0;

                        String endLine = serverReply.substring(0, endIndex);

                        Log.v("MyLog", endLine);

                        if (endLine.contains("CONNECT_OK<END>")) {
                            client_state = States.CONNECT_OK;
                        } else if (endLine.contains("QUERY_REPLY")) {
                            queryReply = endLine;
                        } else if (endLine.contains("UPDATE")) {
                            if (endLine.contains("UPDATE_ONLINE")) {
                            } else {
                                synchronized (updateMsg) {
                                    updateMsg += endLine;
                                }
                            }
                        } else if (endLine.contains("MSG")) {
                            String tmp;
                            tmp = endLine.replace("<END>", "");
                            tmp = tmp.replace("MSG\t", "");
                            msg = tmp;
                        }

                        serverReply = serverReply.replace(endLine, "");
                    }


                    switch (client_state) {
                        case States.CONNECT_INITIALZING:
                            outStream = CharBuffer.wrap(Command.CONNECT_SERVER);
                            while (outStream.hasRemaining()) {
                                socketChannel.write(Charset.defaultCharset().encode(outStream));
                            }
                            Thread.sleep(2000);
                            outStream.clear();
                            break;
                        case States.CONNECT_OK:
                            if (cmd.length() > 0) {
                                outStream = CharBuffer.wrap(cmd);
                                Log.v("MyLog", cmd);
                                while (outStream.hasRemaining()) {
                                    socketChannel.write(Charset.defaultCharset().encode(outStream));
                                }
                                cmd = "";
                                outStream.clear();
                            }
                            break;
                    }
                }
            }
        } catch (InterruptedException e) {
            Log.e("MyLog", "InterruptedException " + e.toString());

        } catch (IOException e) {
            if (e.toString().contains("Server disconnect") || e.toString().contains("SocketTimeoutException")) {
                Log.v("Client", "Reconnect!!");
                mainActivity.runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                mainActivity.restartActivity(States.SERVER_RESTART);
                            }
                        }
                );
            }
        } finally {
            try {
                if (socketChannel != null)
                    socketChannel.close();
            } catch (IOException err) {
                Log.v("MyLog", "IOException " + err.toString());
            }
        }
    }

    public void setTerminated() {
        isTerminated = true;
    }

    public int getClientState() {
        return client_state;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }


    public String getQueryReply() {
        return queryReply;
    }

    public String getMsg() {
        return msg;
    }

    public synchronized String getUpdateMsg() {
        return updateMsg;
    }

    public void resetQueryReply() {
        queryReply = "";
    }

    public void resetMsg() {
        msg = "";
    }

    public synchronized void resetUpdateMsg() {
        updateMsg = "";
    }

}
