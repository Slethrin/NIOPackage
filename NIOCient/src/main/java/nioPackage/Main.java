package nioPackage;

import nioPackage.NioClient.NioClient;

public class Main {

    public static void main(String[] args) {
        int numberOfClients = 3;
         String[] LOGIN_MESSAGES = {
                "{\"uSer\": \"SmartWatch1\", \"pWd\":\"SmartWatch348091\", \"secret1\":\"Watch11\"}",
                "{\"uSer\": \"SmartWatch2\", \"pWd\":\"SmartWatch876746\", \"secret1\":\"Watch12\"}",
                "{\"uSer\": \"SmartWatch3\", \"pWd\":\"SmartWatch165965\", \"secret1\":\"Watch13\"}",
                "{\"uSer\": \"SmartWatch4\", \"pWd\":\"SmartWatch345279\", \"secret1\":\"Watch14\"}",
                "{\"uSer\": \"SmartWatch5\", \"pWd\":\"SmartWatch976455\", \"secret1\":\"Watch15\"}",
                "{\"uSer\": \"SmartWatch6\", \"pWd\":\"SmartWatch938966\", \"secret1\":\"Watch16\"}",
                "{\"uSer\": \"SmartWatch7\", \"pWd\":\"SmartWatch312767\", \"secret1\":\"Watch17\"}"
        };
        for (int i = 1; i <= numberOfClients; i++) {
            String credential = LOGIN_MESSAGES[i-1];
            Thread clientThread = new Thread(new NioClient(credential));
            clientThread.setName("ClientThread-" + i);
            clientThread.start();

            System.out.println("Started thread: " + clientThread.getName() + " with credential: " + credential);
        }
    }
}
