package com.example.hide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.ListIterator;

public class IoU {

    Context contesto;                                                                               				// Contesto della activity in cui avviene la rilevazione degli oggetti

    public IoU(Context cont)
    {
        contesto = cont;
    }

    public void detection()                                                                         				// Metodo che effettua la rilevazione sulle immagini di test
    {
        int numImmagini = 0;                                                                        				// Numero di immagini analizzate

        Log.d("IoU", "Inizio detection");
        try {
            File directory = new File(Environment.getExternalStorageDirectory() + "/DCIM/test");                   	// Apro la directory contenente le immagini su cui fare i test

            for (String filename : directory.list()) {                                                             	// Per ogni file all'interno della directory di test
                Log.d("IoU", String.valueOf(numImmagini+1) + " - Inizio analisi " + filename);

                File immagineFile = new File(Environment.getExternalStorageDirectory() + "/DCIM/test", filename);  	// Apro l'immagine di test
                Bitmap immagine = BitmapFactory.decodeFile(immagineFile.getAbsolutePath());                        	// Creo il bitmap dell'immagine di test
                TensorImage immagineTF = TensorImage.fromBitmap(immagine);
                ObjectDetector detector = ObjectDetector.createFromFileAndOptions(contesto, "android0.tflite", ObjectDetector.ObjectDetectorOptions.builder().setMaxResults(40).setScoreThreshold(0.25f).build());
                List<Detection> risultati = detector.detect(immagineTF);
                ListIterator<Detection> iteratoreRisultati = risultati.listIterator();                             	// Effettuo la detection dell'immagine di test ed elaboro i risultati

                // Apro in scrittura un file associato all'immagine di test analizzata, all'interno del quale inserirò tutte le coordinate delle box associate agli oggetti rilevati
                File risultatoFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), filename.replace(".jpg", "_.txt"));
                risultatoFile.setWritable(true, false);

                BufferedWriter bwFile = new BufferedWriter(new FileWriter(risultatoFile, false));

                while(iteratoreRisultati.hasNext()) {                                               				// Per ogni oggetto rilevato
                    Detection det = iteratoreRisultati.next();
                    RectF boxF = det.getBoundingBox();                                              				// Ottengo le coordinate della box
                    Rect box = new Rect();
                    boxF.roundOut(box);

                    // Salvo le coordinate della box nel file creato
                    bwFile.append(String.valueOf(box.left) + " " + String.valueOf(box.top) + " " + String.valueOf(box.right) + " " + String.valueOf(box.bottom) + "\n");
                }
                bwFile.close();

                numImmagini += 1;                                                                   				// Incremento il numero di immagini analizzate
                Log.d("IoU", String.valueOf(numImmagini) + " - Fine analisi " + filename);
            }
        }
        catch (IOException e) {
            Log.d("IoU", e.getMessage());
            e.printStackTrace();
        }

        Log.d("IoU", "Fine detection " + String.valueOf(numImmagini));
    }
}