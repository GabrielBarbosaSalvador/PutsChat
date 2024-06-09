import java.io.*;
import java.net.*;

public class Cliente {
    public static void main(String argv[]) throws Exception {

        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        String nomeUsuario = null;
        System.out.print("Digite seu nome: ");
        nomeUsuario = inFromUser.readLine();
        Socket clientSocket = new Socket("localhost", 6789);
        DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        outToServer.writeBytes(nomeUsuario + '\n');
        String finalNomeUsuario = nomeUsuario;

        Runnable sendHandler = () -> {
            while (true) {

                String sentence = null;
                try {
                    sentence = inFromUser.readLine();
                    if (sentence.equalsIgnoreCase("sair")) {
                        System.out.println("Você saiu do chat.");
                        clientSocket.close();
                        break;
                    }
                    outToServer.writeBytes(finalNomeUsuario + ": " + sentence + '\n');
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Runnable receiveHandler = () -> {
            while (true) {
                String mensagemDoServidor = null;
                try {
                    mensagemDoServidor = inFromServer.readLine();
                    System.out.println(mensagemDoServidor);

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };


        String mensagemDoServidor = null;
        try {
            System.out.println("Aguardando conexão...");

            mensagemDoServidor = inFromServer.readLine();
            if (mensagemDoServidor.equalsIgnoreCase("OK"))
            {
                Thread receiveThread = new Thread(receiveHandler);
                Thread sendThread = new Thread(sendHandler);
                System.out.println("Conexão aceita.");
                receiveThread.start();
                sendThread.start();


            }
            else
            {
                System.out.println("Conexão recusada.");
                clientSocket.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
