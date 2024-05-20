
import java.io.Console;

public class Launcher {
    
    public void run() throws Exception {
         
        // écoute les commandes
        boolean keepGoing = true;
        final Console console = System.console();
        while(keepGoing) {
            final String commande = console.readLine("$ ");
            if(commande == null) break;

            keepGoing = processCommand(commande.trim());
        }
    }

    private boolean processCommand(String cmd) throws Exception {
        if(("quit").equals(cmd)) {
            // TODO shutdown
            return false;
        }
        
        if(("cancel").equals(cmd)) {
            // TODO cancel task

        } else if(("status").equals(cmd)) {
            // TODO show workers status

        } else if(("help").equals(cmd.trim())) {
            System.out.println(" • status - display informations about connected workers");
            System.out.println(" • solve <d> - try to mine with given difficulty");
            System.out.println(" • cancel - cancel a task");
            System.out.println(" • help - describe available commands");
            System.out.println(" • quit - terminate pending work and quit");
                
        } else if(cmd.startsWith("solve")) {
            // TODO start solving ...
        }

        return true;
    }

    public static void main(String[] args) throws Exception {
        new Launcher().run();
    }

}
