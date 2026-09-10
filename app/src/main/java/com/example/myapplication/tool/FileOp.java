package com.example.myapplication.tool;

import android.content.Context;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import kotlinx.coroutines.flow.internal.ChannelFlowOperatorImpl;

public class FileOp {
    public static boolean save(Context context,String text,String path){
        FileOutputStream fileOutputStream=null;
        try{
            fileOutputStream=context.openFileOutput(path,Context.MODE_PRIVATE);
            fileOutputStream.write(text.getBytes());
            return true;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }finally {
            try{
                if(fileOutputStream!=null) fileOutputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static String get(Context context,String path) throws FileNotFoundException {
        String content="";
        FileInputStream fileInputStream=null;
        try{
            fileInputStream= context.openFileInput(path);
            byte[] buffer=new byte[fileInputStream.available()];
            fileInputStream.read(buffer);
            content=new String(buffer);
            return content;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }finally {
            try{
                if(fileInputStream==null)fileInputStream.close();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
