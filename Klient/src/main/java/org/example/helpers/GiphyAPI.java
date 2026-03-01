package org.example.helpers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

public class GiphyAPI {
    private static final String API_KEY = "NpCRJe4i5UivlUS5Vue1yk4PbOytBcno";
    private static final String BASE_URL = "https://api.giphy.com/v1/gifs/search";

    public static List<String> searchGifs(String query) {
        List<String> urls = new ArrayList();

        try {
            String sanitizedQuery = query.trim().replace(" ", "+");
            String urlString = "https://api.giphy.com/v1/gifs/search?api_key=NpCRJe4i5UivlUS5Vue1yk4PbOytBcno&q=" + sanitizedQuery + "&limit=12";
            URL url = new URL(urlString);
            HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
            conn.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder();

            String inputLine;
            while((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }

            in.close();
            String json = content.toString();
            Pattern pattern = Pattern.compile("\"fixed_height\":\\{.*?\"url\":\"(.*?)\"");
            Matcher matcher = pattern.matcher(json);

            while(matcher.find()) {
                String rawUrl = matcher.group(1).replace("\\/", "/");
                urls.add(rawUrl);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return urls;
    }
}
