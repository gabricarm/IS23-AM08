package it.polimi.ingsw.utilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * SINGLETON
 */
public class JsonWithExposeSingleton {
    /**
     *
     */
    private static Gson json;

    /**
     *
     */
    private JsonWithExposeSingleton(){}

    /**
     *
     */
    public static Gson getJsonWithExposeSingleton(){
        if(json==null) json=new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();

        return json;
    }

}