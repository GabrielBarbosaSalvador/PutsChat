import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Cliente2 {
    public static void main(String argv[]) throws Exception {
        String nomeUsuario = null;
        AtomicBoolean saidaVoluntaria = new AtomicBoolean(false);

        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in)); // buffer do usuário

        System.out.print("Digite seu nome: ");
        nomeUsuario = inFromUser.readLine().trim(); // lê uma linha digitada

        Socket clientSocket = new Socket("localhost", 6789); // conecta ao servidor

        DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream()); // buffer que vai para o servidor
        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); // buffer vem do servidor

        outToServer.writeBytes(nomeUsuario + '\n'); // envia ao servidor o nome do usuário

        Runnable sendMessageHandler = () -> {
            while (!saidaVoluntaria.get())
            {
                String mensagem = null;
                try
                {
                    mensagem = inFromUser.readLine(); // lê algo digitado no terminal do usuário
                    outToServer.writeBytes(mensagem + '\n'); //envia para o servidor a mensagem

                    if (mensagem.equalsIgnoreCase("!sair"))
                    {
                        saidaVoluntaria.set(true);
                    }

                } catch (IOException e) {
                    if (!saidaVoluntaria.get()) // conexão ter falhado foi pelo comando !sair?
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        };

        Runnable receiveMessageHandler = () -> {
            while (!saidaVoluntaria.get())
            {
                String mensagemDoServidor = null;

                try
                {

                    mensagemDoServidor = inFromServer.readLine();

                    if (mensagemDoServidor.contains("@FIN@")) // determina o FIN da conexão
                    {
                        System.out.println("\033[1;3;30;42mVocê saiu do chat.\033[0m");
                    }
                    else
                    {
                        if (mensagemDoServidor != null)
                            System.out.println(mensagemDoServidor);
                    }


                } catch (IOException e) {
                    if (!saidaVoluntaria.get()) // conexão ter falhado foi pelo comando !sair?
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        };


        String mensagemDoServidor = null;
        try {
            System.out.println("Aguardando aprovação...");
            mensagemDoServidor = inFromServer.readLine(); // lê a mensagem do servidor

            if (mensagemDoServidor.equalsIgnoreCase("OK")) // se for OK, todos os usuários permitiram a conexão
            {
                Thread receiveThread = new Thread(receiveMessageHandler); // cria uma thread para ouvir as mensagens do servidor
                Thread sendThread = new Thread(sendMessageHandler); // cria uma thread para enviar mensagens ao servidor

                System.out.println("Você foi aprovado.");

                receiveThread.start();
                sendThread.start();
            }
            else // todos do servidor recusaram a conexão
            {
                System.out.println("Você foi recusado.");
                clientSocket.close();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
