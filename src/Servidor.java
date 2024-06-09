
import java.io.*;
import java.net.*;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

public class Servidor {

    private final static char identificadorComandoParaServidor = '!';

    public static boolean isCommandToServer(String message){
        return !message.isEmpty() && message.charAt(0) == identificadorComandoParaServidor;
    }

    public static String generateServerMessage(String message){
        return "\033[1;3;30;42mMensagem do servidor: "+message+"\033[0m";
    }

    public static void main(String argv[]) throws Exception
    {
        LinkedList<Usuario> usuariosConectados = new LinkedList<>();
        LinkedList<Socket> usuariosPendentes = new LinkedList<>();

        ServerSocket welcomeSocket = new ServerSocket(6789);

        Runnable connectionHandler = () -> 
        {
            while (true) 
            {
                try 
                {
                    System.out.println("Waiting for a new connection...");
                    Socket connectionSocket = welcomeSocket.accept();
                    System.out.println("New connection accepted from " + connectionSocket.getInetAddress().getHostAddress());

                    synchronized (usuariosPendentes) 
                    {
                        usuariosPendentes.add(connectionSocket);
                        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(usuariosPendentes.getFirst().getInputStream()));
                        Singleton.nome = inFromClient.readLine();
                    }
                    
                } 
                catch (IOException e) 
                {
                    e.printStackTrace();
                    break;
                }
            }
        };

        Thread connectionThread = new Thread(connectionHandler);
        connectionThread.start();

        Runnable userHandler = () -> {
            while (true)
            {
                boolean enviaMensagemDoUsuarioParaTodos = true;
                String mensagem = null;
                int quantidadeUsuariosConectados;
                int removeuUsuario = 0;

                synchronized (usuariosConectados)
                {
                    quantidadeUsuariosConectados = usuariosConectados.size();
                }

                for (int i=0; i<quantidadeUsuariosConectados-removeuUsuario; i++)
                {
                    try
                    {
                        Usuario usuarioReceive = usuariosConectados.get(i);
                        if(usuarioReceive.getInFromClient().ready())
                        {
                            mensagem = usuarioReceive.getInFromClient().readLine();

                            if(isCommandToServer(mensagem)) // identifica se a mensagem recebida é válida e é algum tipo de comando ao servidor
                            {
                                if(!usuarioReceive.isVote() && Singleton.contador > 0) //usuário não votou e tem alguém tentando conectar
                                {
                                    if (mensagem.equalsIgnoreCase(identificadorComandoParaServidor+"sim"))
                                    {
                                        usuarioReceive.setAccept(true);
                                        usuarioReceive.setVote(true);
                                        Singleton.contador--;

                                        System.out.println("Usuário '"+usuarioReceive.getNome()+"' aceitou.");
                                    }
                                    else if (mensagem.equalsIgnoreCase(identificadorComandoParaServidor+"nao"))
                                    {
                                        usuarioReceive.setAccept(false);
                                        usuarioReceive.setVote(true);
                                        Singleton.contador--;

                                        System.out.println("Usuário não aceitou.");

                                    }
                                    else
                                    {
                                        System.out.println("Comando não reconhecido. Mensagem ignorada.");
                                    }

                                }
                                else if (Singleton.contador == 0) // não tem nenhuma ação do servidor sendo executada
                                {
                                    if (mensagem.equalsIgnoreCase(identificadorComandoParaServidor+"sair")) // usuário está querendo sair?
                                    {
                                        Usuario usuarioSaindo = usuarioReceive;
                                        System.out.println("'"+usuarioSaindo.getNome()+"' optou por sair do chat.\n");

                                        for (int j=0; j<quantidadeUsuariosConectados; j++)
                                        {
                                            Usuario usuarioSend = usuariosConectados.get(j);
                                            if (usuarioSend != usuarioSaindo) // o usuário a enviar a mensagem é diferente do que está saindo?
                                            {
                                                usuarioSend.getOutToClient().writeBytes(generateServerMessage("'"+usuarioSaindo.getNome()+"' saiu do chat.\n"));
                                            }
                                            else
                                            {
                                                usuarioSaindo.getOutToClient().writeBytes("@FIN@\n"); // envia mensagem de FIN para finalizar o socket do lado do cliente
                                            }
                                        }

                                        usuariosConectados.remove(i); //remove o usuário da lista
                                        usuarioSaindo.getConnectionSocket().close(); //fecha o socket
                                        enviaMensagemDoUsuarioParaTodos = false;
                                        removeuUsuario = 1;
                                    }
                                }
                            }

                            if (enviaMensagemDoUsuarioParaTodos)
                            {
                                for (int j=0; j<quantidadeUsuariosConectados; j++)
                                {
                                    Usuario usuarioSend = usuariosConectados.get(j);
                                    if (usuarioSend != usuarioReceive)
                                    {
                                        try
                                        {
                                            usuarioSend.getOutToClient().writeBytes("\033[1m"+usuarioReceive.getNome()+":\033[0m "+mensagem + "\n");
                                        }
                                        catch (IOException e)
                                        {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                }
                            }
                        }

                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };

        Thread userThread = new Thread(userHandler);
        userThread.start();

        Runnable managerHandler = () -> {
            while (true) {
                try {
                    synchronized (usuariosPendentes)
                    {
                        if (!usuariosPendentes.isEmpty())
                        {
                            if (usuariosPendentes.getFirst().isConnected())
                            {
                                if (usuariosConectados.isEmpty())
                                {
                                    System.out.println("-----------------\nPRIMEIRO USUÁRIO\n-----------------");
                                    Singleton.aux = usuariosPendentes.getFirst();
                                    usuariosPendentes.removeFirst();
                                    Usuario usuario = new Usuario(true, Singleton.aux);
                                    usuario.setNome(Singleton.nome);

                                    synchronized (usuariosConectados)
                                    {
                                        usuariosConectados.add(usuario);
                                    }

                                    usuario.getOutToClient().writeBytes("OK\n");
                                    usuario.getOutToClient().writeBytes(generateServerMessage("Bem vindo ao chat, " + usuario.getNome() + "!\n"));
                                }
                                else
                                {
                                    System.out.println("-----------------\nVOTAÇÃO INICIADA\n-----------------");
                                    Singleton.contador = usuariosConectados.size();
                                    for(Usuario users : usuariosConectados)
                                    {
                                        users.getOutToClient().writeBytes(generateServerMessage("'"+Singleton.nome+"' quer entrar ao chat ("+identificadorComandoParaServidor+"sim para aceitar e "+identificadorComandoParaServidor+"nao para recusar)\n"));
                                    }

                                    while (Singleton.contador > 0) // aguarda enquanto todos os usuários não responderem
                                    {
                                        Thread.sleep(4000); // Pausa a execução da thread por 5 segundos
                                        System.out.println("Faltam: " + Singleton.contador + " usuários votar(em).");
                                    }
                                    System.out.println("-----------------\nVOTAÇÃO FINALIZADA\n-----------------");

                                    boolean usuarioAceito = true; // por default, o usuário foi aceito
                                    for (Usuario usuario : usuariosConectados) // percorre a resposta de todos
                                    {
                                        if (!usuario.isAccept()) // o usuário atual não aceitou o novo usuário?
                                        {
                                            usuarioAceito = false; // seta que o novo usuário não foi aceito.
                                        }

                                        usuario.setVote(false); // reseta a votação
                                    }

                                    if (usuarioAceito) // o novo usuário foi aceito?
                                    {
                                        Singleton.aux = usuariosPendentes.removeFirst(); //pega o primeiro dos pendentes

                                        Usuario usuario = new Usuario(true, Singleton.aux);
                                        usuario.setNome(Singleton.nome);

                                        usuario.getOutToClient().writeBytes("OK\n"); // envia ao cliente que ele foi permitido

                                        usuariosConectados.add(usuario); // adiciona o cliente a lista de conectados

                                        for(Usuario users : usuariosConectados) // percorre todos os conectados
                                        {
                                            if(users == usuario) // se o usuário for igual ao que está entrando no chat
                                            {
                                                users.getOutToClient().writeBytes(generateServerMessage("Bem vindo ao chat, " + Singleton.nome + "!\n"));
                                            }
                                            else // se for qualquer outro usuário
                                            {
                                                users.getOutToClient().writeBytes(generateServerMessage("'"+Singleton.nome+ "' entrou no chat.\n"));
                                            }
                                        }
                                    }
                                    else
                                    {
                                        Usuario usuario = new Usuario(true, usuariosPendentes.getFirst());
                                        usuario.getOutToClient().writeBytes(generateServerMessage("Você não foi permitido :(.\n"));
                                        usuario = null;

                                        usuariosPendentes.getFirst().close();
                                        usuariosPendentes.removeFirst();
                                    }
                                }
                            } else {
                                usuariosPendentes.getFirst().close();
                                usuariosPendentes.removeFirst();
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }
            }
        };


        Thread managerThread = new Thread(managerHandler);
        managerThread.start();
    }
}


