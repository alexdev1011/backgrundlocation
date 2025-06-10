package com.alexdev1011.plugins.backgroundlocation;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import kotlin.text.Charsets;

public class LocalStorage {
    Context context;
    LocalStorage(Context context ){
        this.context = context;
    }

    public String getItem( String key ){
        File file = new File(context.getFilesDir(),key);
        StringBuilder text = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
            return text.toString();
        }
        catch (IOException e) {
            //You'll need to add proper error handling here
            return null;
        }
    }

    public Object getObject( String key ){
        try {
            FileInputStream fis = context.openFileInput(key);
            ObjectInputStream is = new ObjectInputStream(fis);
            Object o = is.readObject();
            is.close();
            fis.close();
            return o;
        } catch (FileNotFoundException e) {
            //e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean setObject (String key , Object object  ) throws IOException {
        FileOutputStream fos = context.openFileOutput(key, Context.MODE_PRIVATE);
        ObjectOutputStream os = new ObjectOutputStream(fos);
        if(os != null)
            os.writeObject(object);
        os.close();
        fos.close();
        return true;
    }

    public boolean setItem( String key , String content ){
        try (FileOutputStream fos = context.openFileOutput(key, Context.MODE_PRIVATE)) {
            fos.write(content.getBytes(Charsets.UTF_8));
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public boolean deleteItem( String key  ){
        try (FileOutputStream fos = context.openFileOutput(key, Context.MODE_PRIVATE)) {
            fos.write(null);
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean removeItem( String key ){
        File file = new File(context.getFilesDir(),key);
        return file.delete();
    }
}
