package com.example.hide;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import com.google.common.util.concurrent.ListenableFuture;
import org.jcodec.api.android.AndroidSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Rational;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ImageAnalysis.Analyzer {

    final static int PIXELATION_SIZE = 6;                                                           // Dimensione pixelation
    private String coloreAttivo = "#018786";                                                        // Colore usato per indicare quando un componente nella schermata è selezionato

    final static int MODALITA_LIVE = 0;                                                             // Identificativo per la modalità live
    final static int MODALITA_FOTO = 1;                                                             // Identificativo per la modalità foto
    final static int MODALITA_VIDEO = 2;                                                            // Identificativo per la modalità video
    private int modalitaAttiva;                                                                     // Indicatore di qual è la modalità selezionata dall'utente

    private ListenableFuture<ProcessCameraProvider> provider;                                       // Camera provider
    private ImageCapture catturaImmagine;                                                           // Componente usato per catturare immagini
    private ImageAnalysis analisiImmagine;                                                          // Componente usato per l'analisi delle immagini catturate
    private VideoCapture catturaVideo;                                                              // Componente usato per registrare video
    private int orientamentoCamera;                                                                 // 0 -> Uso fotocamera frontale | 1 -> Uso fotocamera posteriore
    private boolean analisiAttiva;                                                                  // TRUE -> Analisi attiva | FALSE -> Analisi non attiva
    private boolean analisiAttivaLive;                                                              // TRUE -> Un thread sta già analizzando l'immagine | FALSE -> Bisogna creare il thread che analizzi l'immagine live
    private int tipoAnalisi;                                                                        // 0 -> Prestazioni: più veloce, meno precisa | 1 -> Precisione: più precisa, meno veloce
    
    private boolean rilevVolti, rilevTesti;                                                         // TRUE -> Durante l'analisi rilevo volti/testi | FALSE -> Durante l'analisi non rilevo volti/testi
    private int sogliaVolti, sogliaTesti, numeroOggetti;                                            // Affidabilità minima da superare per sfocare l'oggetto rilevato | Numero massimo di oggetti da rilevare nella scena
    
    private Bitmap imgPhotoMode;                                                                    // Bitmap dell'immagine catturata con la fotocamera o caricata dalla galleria nella modalità foto
    private Uri uriVideo;                                                                           // Uri del video registrato con la fotocamera o caricato dalla galleria in modalità video
    private Thread threadAnalisiVideo;                                                              // Thread utilizzato per analizzare un video
    private boolean registrandoVideo;                                                               // TRUE -> Registrazione in corso di un video | FALSE -> Registrazione di un video non ancora iniziata o terminata

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Objects.requireNonNull(getSupportActionBar()).hide();

        threadAnalisiVideo = null;

        // IMPOSTAZIONI

        ((SeekBar) findViewById(R.id.Imp_SogliaVoltiSB)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {                 // Selettore soglia minima per il rilevamento di volti
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ((TextView) findViewById(R.id.Imp_SogliaVoltiT)).setText("SOGLIA RILEVAMENTO VOLTI - " + String.valueOf(progress) + "%");   // Visualizzo il valore di soglia selezionato
                sogliaVolti = progress;                                                                                                     // Aggiorno il valore della soglia
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ((SeekBar) findViewById(R.id.Imp_SogliaTestiSB)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {                 // Selettore soglia minima per il rilevamento di testi
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ((TextView) findViewById(R.id.Imp_SogliaTestiT)).setText("SOGLIA RILEVAMENTO TESTI - " + String.valueOf(progress) + "%");   // Visualizzo il valore della soglia selezionato
                sogliaTesti = progress;                                                                                                     // Aggiorno il valore della soglia
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ((SeekBar) findViewById(R.id.Imp_NumeroOggettiSB)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {               // Selettore numero oggetti da rilevare
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ((TextView) findViewById(R.id.Imp_NumeroOggettiT)).setText("NUMERO OGGETTI DA RILEVARE - " + String.valueOf(progress));     // Visualizzo il numero di oggetti da rilevare
                numeroOggetti = progress;                                                                                                   // Aggiorno il numero di oggetti da rilevare
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        findViewById(R.id.LayoutImpostazioni).setVisibility(View.GONE);                             // Inizio con la schermata delle impostazioni nascosta

        // Impostazioni iniziali:
        // - Rilevamento dei volti e dei testi attivato
        // - Soglia di affidabilità minima per volti e testi pari al 25%
        // - Numero massimo di oggetti da rilevare pari a 25 (valore massimo)
        // - Tipo di analisi: PRESTAZIONI (più veloce, meno precisa)
        rilevVolti = true;
        rilevTesti = true;
        sogliaVolti = 25;
        sogliaTesti = 25;
        numeroOggetti = 25;
        tipoAnalisi = 0;

        // GESTIONE FOTOCAMERA

        orientamentoCamera = CameraSelector.LENS_FACING_BACK;                                       // Inizio con la fotocamera posteriore

        provider = ProcessCameraProvider.getInstance(this);                                  // Ottengo il provider della fotocamera
        provider.addListener(() ->
        {
            try {
                avviaFotocamera(provider.get());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, getExecutor());

        cambioModalita(MODALITA_LIVE);                                                              // Inizio con la modalità live
    }

    // Gestore delle azioni che devono compiere tutti gli elementi cliccabili nella schermata
    @Override
    public void onClick(View view) {
        switch (view.getId())
        {
            // GESTIONE FOTOCAMERA

            case R.id.GiraFotocameraB:                                                              // Rotazione fotocamera
                giraFotocamera();
                break;

            // CAMBIO MODALITA

            case R.id.LiveModeB:                                                                    // Passaggio in modalità live
                cambioModalita(MODALITA_LIVE);
                break;

            case R.id.PhotoModeB:                                                                   // Passaggio in modalità foto
                cambioModalita(MODALITA_FOTO);
                break;

            case R.id.VideoModeB:                                                                   // Passaggio in modalità video
                cambioModalita(MODALITA_VIDEO);
                break;

            // MODALITA' LIVE - FOTO - VIDEO

            case R.id.ScattaRegistraB:                                                              // Scattare foto o registrare video
                if(modalitaAttiva == MODALITA_FOTO) {
                    scattaFoto();
                } else if (modalitaAttiva == MODALITA_VIDEO) {
                    registraVideo();
                }
                break;

            case R.id.CaricaMediaB:                                                                 // Caricare foto o video dalla galleria
                if(modalitaAttiva == MODALITA_FOTO) {
                    caricaFoto();
                } else if (modalitaAttiva == MODALITA_VIDEO) {
                    caricaVideo();
                }
                break;

            case R.id.NuovoB:                                                                       // Torno indietro dalla schermata di analisi video/foto alla schermata per scattare una nuova foto o registrare un nuovo video
                if(threadAnalisiVideo != null) {                                                    // Se in background è in esecuzione il thread per analizzare il video
                    threadAnalisiVideo.interrupt();                                                 // Interrompo il thread di analisi video
                }

                cambioModalita(modalitaAttiva);
                break;

            case R.id.AnalizzaB:                                                                    // Analizzare foto e video nelle varie modalità
                switch (modalitaAttiva)
                {
                    case MODALITA_LIVE:
                        analisiAttiva = !analisiAttiva;                                             // Attivo/disattivo l'analisi live

                        if(analisiAttiva) {
                            ((ImageView) findViewById(R.id.ImmagineView)).setImageBitmap(((PreviewView) findViewById(R.id.PreviewView)).getBitmap());
                            ((ImageButton) findViewById(R.id.AnalizzaB)).setColorFilter(Color.parseColor(coloreAttivo));
                            analisiAttivaLive = true;
                        } else {
                            ((ImageButton) findViewById(R.id.AnalizzaB)).setColorFilter(Color.WHITE);
                            cambioModalita(MODALITA_LIVE);
                        }
                        break;

                    case MODALITA_FOTO:
                        analisiFoto();                                                              // Invoco il metodo per analizzare la foto
                        break;

                    case MODALITA_VIDEO:
                        analisiVideo();                                                             // Invoco il metodo per analizzare il video
                        break;
                }
                break;

            // IMPOSTAZIONI

            case R.id.ImpostazioniB:                                                                // Mostrare la schermata delle impostazioni
                cambioModalita(modalitaAttiva);
                findViewById(R.id.LayoutModalita).setVisibility(View.GONE);
                findViewById(R.id.LayoutView).setVisibility(View.GONE);
                findViewById(R.id.LayoutTasti).setVisibility(View.GONE);
                findViewById(R.id.LayoutImpostazioni).setVisibility(View.VISIBLE);
                break;

            case R.id.Imp_Salva:                                                                    // Nascondere la schermata e salvare i nuovi valori delle impostazioni
                try {
                    avviaFotocamera(provider.get());

                    rilevVolti = ((CheckBox) findViewById(R.id.Imp_RilevaVolti)).isChecked();
                    rilevTesti = ((CheckBox) findViewById(R.id.Imp_RilevaTesti)).isChecked();
                    tipoAnalisi = ((RadioButton) findViewById(R.id.PrestazioniRB)).isChecked() ? 0 : 1;

                    findViewById(R.id.LayoutImpostazioni).setVisibility(View.GONE);
                    findViewById(R.id.LayoutModalita).setVisibility(View.VISIBLE);
                    findViewById(R.id.LayoutView).setVisibility(View.VISIBLE);
                    findViewById(R.id.LayoutTasti).setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    // Override del metodo che controlla il comportamento del tasto per tornare indietro
    @Override
    public void onBackPressed() {
        // Se mi trovo nella schermata delle impostazioni, il tasto per tornare indietro viene blocato
        // Questo viene fatto per far sì che l'utente possa uscire dalla schermata delle impostazioni solo dopo aver salvato le modifiche effettuate
        // Se non mi trovo nella schermata della impostazioni, il tasto per tornare indietro porta alla activity precedente

        if (findViewById(R.id.LayoutImpostazioni).getVisibility() == View.GONE) {
            super.onBackPressed();
        }
    }

    // GESTIONE FOTOCAMERA

    // Metodo per gestire l'avvio della fotocamera
    @SuppressLint("RestrictedApi")
    private void avviaFotocamera(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        CameraSelector camSelector = new CameraSelector.Builder().requireLensFacing(orientamentoCamera).build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(((PreviewView) findViewById(R.id.PreviewView)).getSurfaceProvider());

        catturaImmagine = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
        analisiImmagine = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        analisiImmagine.setAnalyzer(getExecutor(), this);
        catturaVideo = new VideoCapture.Builder().setVideoFrameRate(30).build();

        if(modalitaAttiva == MODALITA_LIVE || modalitaAttiva == MODALITA_FOTO)                      // Se sono in modalità live o foto, imposto il provider per effettuare la cattura e l'analisi live delle immagini
        {
            cameraProvider.bindToLifecycle((LifecycleOwner) this, camSelector, preview, catturaImmagine, analisiImmagine);
        }
        else                                                                                        // Se sono in modalità video, imposto il provider per effettuare la cattura dei video
        {
            cameraProvider.bindToLifecycle((LifecycleOwner) this, camSelector, preview, catturaVideo);
        }

        findViewById(R.id.PreviewView).setVisibility(View.VISIBLE);                                 // Mostro la preview nella quale verrà mostrato il flusso dei fotogrammi catturati direttamente dalla fotocamera
    }

    // Metodo per cambiare l'orientamento della fotocamera
    private void giraFotocamera() {
        cambioModalita(modalitaAttiva);                                                             // Resetto la schermata in cui mi trovo

        try {
            orientamentoCamera = orientamentoCamera == CameraSelector.LENS_FACING_FRONT ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
            avviaFotocamera(provider.get());                                                        // Riavvio la fotocamera con il nuovo orientamento
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    // GESTIONE MODALITA' FOTO

    // Metodo per scattare una fotografia
    public void scattaFoto() {
        String nomeFoto = "SISDIG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpeg";     // Nome del file contenente la foto

        catturaImmagine.takePicture(
                getExecutor(),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(ImageProxy immagine) {
                        ContentValues dettagliFoto = new ContentValues();                           // Imposto i dettagli del file della foto
                        dettagliFoto.put(MediaStore.Images.Media._ID, nomeFoto);
                        dettagliFoto.put(MediaStore.Images.Media.ORIENTATION, String.valueOf(-immagine.getImageInfo().getRotationDegrees()));
                        dettagliFoto.put(MediaStore.Images.Media.DISPLAY_NAME, nomeFoto);
                        dettagliFoto.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                        dettagliFoto.put(MediaStore.Images.Media.WIDTH, immagine.getWidth());
                        dettagliFoto.put(MediaStore.Images.Media.HEIGHT, immagine.getHeight());
                        dettagliFoto.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Hide");
                        OutputStream outputStream = null;

                        try {
                            Uri URIImmagine = getApplicationContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, dettagliFoto);      // Salvo la foto
                            outputStream = getApplicationContext().getContentResolver().openOutputStream(URIImmagine);
                            imgPhotoMode = ((PreviewView) findViewById(R.id.PreviewView)).getBitmap();                                                              // Ottengo il bitmap della foto scattata

                            if (!imgPhotoMode.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)) {                                                      // Se ci sono problemi con il salvataggio della foto
                                Toast.makeText(getApplicationContext(), "Errore salvataggio foto nella galleria", Toast.LENGTH_SHORT).show();
                            }
                            immagine.close();
                            outputStream.close();

                            ((ImageView) findViewById(R.id.ImmagineView)).setImageBitmap(imgPhotoMode);     // Mostro l'immagine della foto scattata assieme ai button necessari per analizzarla
                            findViewById(R.id.PreviewView).setVisibility(View.GONE);
                            findViewById(R.id.ImmagineView).setVisibility(View.VISIBLE);
                            findViewById(R.id.NuovoB).setVisibility(View.VISIBLE);
                            findViewById(R.id.GiraFotocameraB).setVisibility(View.GONE);
                            findViewById(R.id.ScattaRegistraB).setVisibility(View.GONE);
                            findViewById(R.id.CaricaMediaB).setVisibility(View.GONE);
                            findViewById(R.id.AnalizzaB).setVisibility(View.VISIBLE);
                        } catch (Exception exception) {
                            exception.printStackTrace();
                            Toast.makeText(getApplicationContext(), "Errore scatto foto: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    // Metodo per caricare una foto dalla galleria
    private void caricaFoto() {
        Intent intentGalleria = new Intent();
        intentGalleria.setType("image/*");                                                          // Filtro per selezionare solo le immagini dalla galleria
        intentGalleria.setAction(Intent.ACTION_GET_CONTENT);
        getImageActivity.launch(intentGalleria);                                                    // Apro la galleria
    }

    // Activity per gestire il caricamento di un'immagine dalla galleria
    ActivityResultLauncher<Intent> getImageActivity = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {                                 // Se la selezione dell'immagine è andata a buon fine
                    Intent data = result.getData();                                                 // Ottengo i dati della selezione effettuata
                    if (data != null && data.getData() != null) {
                        Uri uriImmagineSelezionata = data.getData();                                // Ottengo l'uri che identifica univocamente l'immagine nel sistema
                        ImageDecoder.Source sorgente = ImageDecoder.createSource(getContentResolver(), uriImmagineSelezionata);

                        try {
                            Bitmap immagineCaricata = ImageDecoder.decodeBitmap(sorgente);
                            imgPhotoMode = immagineCaricata.copy(Bitmap.Config.ARGB_8888, true);    // Ottengo il bitmap dell'immagine caricata
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        ((ImageView) findViewById(R.id.ImmagineView)).setImageBitmap(imgPhotoMode); // Mostro l'immagine caricata assieme ai button necessari per analizzarla
                        findViewById(R.id.PreviewView).setVisibility(View.GONE);
                        findViewById(R.id.ImmagineView).setVisibility(View.VISIBLE);
                        findViewById(R.id.NuovoB).setVisibility(View.VISIBLE);
                        findViewById(R.id.GiraFotocameraB).setVisibility(View.GONE);
                        findViewById(R.id.ScattaRegistraB).setVisibility(View.GONE);
                        findViewById(R.id.CaricaMediaB).setVisibility(View.GONE);
                        findViewById(R.id.AnalizzaB).setVisibility(View.VISIBLE);
                    }
                }
            });

    // GESTIONE MODALITA' VIDEO

    // Metodo per registrare un video
    @SuppressLint("RestrictedApi")
    public void registraVideo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String nomeVideo = "SISDIG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4";     // Nome del file che conterrà il video registrato

        if (!registrandoVideo)                                                                      // Se non si sta registrando un video
        {
            registrandoVideo = true;                                                                // Indico che la registrazione del video è iniziata
            ((ImageButton) findViewById(R.id.ScattaRegistraB)).setColorFilter(Color.RED);           // Modifico il colore del button di registrazione per indicare che la registrazione è iniziata
            findViewById(R.id.GiraFotocameraB).setVisibility(View.GONE);
            findViewById(R.id.CaricaMediaB).setVisibility(View.GONE);

            ContentValues dettagliVideo = new ContentValues();                                      // Imposto i dettagli del file del video
            dettagliVideo.put(MediaStore.Images.Media._ID, nomeVideo);
            dettagliVideo.put(MediaStore.Images.Media.DISPLAY_NAME, nomeVideo);
            dettagliVideo.put(MediaStore.Images.Media.MIME_TYPE, "video/mp4");
            dettagliVideo.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Hide");

            catturaVideo.startRecording(                                                            // Inizio la registrazione del video
                    new VideoCapture.OutputFileOptions.Builder(
                            getContentResolver(),
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            dettagliVideo
                    ).build(),
                    getExecutor(),
                    new VideoCapture.OnVideoSavedCallback() {
                        @Override
                        public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {           // Quando la registrazione del video termina e avviene il salvataggio del video acquisito
                            MediaController mediaController = new MediaController(MainActivity.this);            // Controller per gestire la riproduzione del video
                            ((VideoView) findViewById(R.id.VideoView)).setVideoURI(outputFileResults.getSavedUri());    // Imposto il video registrato nella VideoView
                            ((VideoView) findViewById(R.id.VideoView)).setMediaController(mediaController);             // Collego il media controller al video
                            mediaController.setAnchorView(findViewById(R.id.VideoView));
                            ((VideoView) findViewById(R.id.VideoView)).start();                     // Avvio il video

                            uriVideo = outputFileResults.getSavedUri();                             // Salvo l'uri del video registrato
                        }

                        @Override
                        public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                            Toast.makeText(MainActivity.this,"Errore registrazione video: "+ message ,Toast.LENGTH_SHORT).show();
                        }
                    }

            );
        } else {                                                                                    // Se si sta registrando un video
            catturaVideo.stopRecording();                                                           // Termino la registrazopme del video
            registrandoVideo = false;                                                               // Indico che la registrazione del video è terminata

            findViewById(R.id.PreviewView).setVisibility(View.GONE);                                // Mostro il video caricato assieme ai button necessari per analizzarlo
            findViewById(R.id.VideoView).setVisibility(View.VISIBLE);
            findViewById(R.id.NuovoB).setVisibility(View.VISIBLE);
            findViewById(R.id.GiraFotocameraB).setVisibility(View.GONE);
            findViewById(R.id.ScattaRegistraB).setVisibility(View.GONE);
            findViewById(R.id.CaricaMediaB).setVisibility(View.GONE);
            findViewById(R.id.AnalizzaB).setVisibility(View.VISIBLE);
            ((ImageButton) findViewById(R.id.ScattaRegistraB)).setColorFilter(Color.WHITE);
        }
    }

    // Metodo per caricare un video dalla galleria
    private void caricaVideo()
    {
        Intent intentGalleria = new Intent();
        intentGalleria.setType("video/*");                                                          // Filtro per selezionare solo i video dalla galleria
        intentGalleria.setAction(Intent.ACTION_GET_CONTENT);
        getVideoActivity.launch(intentGalleria);                                                    // Apro la galleria
    }

    // Activity per gestire il caricamento di un video dalla galleria
    ActivityResultLauncher<Intent> getVideoActivity = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {                                 // Se la selezione del video è andata a buon fine
                    Intent data = result.getData();                                                 // Ottengo i dati della selezione effettuata
                    if (data != null && data.getData() != null) {
                        uriVideo = data.getData();                                                          // Ottengo l'uri che identifica univocamente il video nel sistema
                        MediaController mediaController = new MediaController(MainActivity.this);    // Controller per gestire la riproduzione del video
                        ((VideoView) findViewById(R.id.VideoView)).setVideoURI(uriVideo);                   // Imposto il video selezionato nella VideoView
                        ((VideoView) findViewById(R.id.VideoView)).setMediaController(mediaController);     // Collego il media controller al video
                        mediaController.setAnchorView(findViewById(R.id.VideoView));
                        ((VideoView) findViewById(R.id.VideoView)).start();                         // Avvio il video

                        findViewById(R.id.PreviewView).setVisibility(View.GONE);                    // Mostro il video caricato assieme ai button necessari per analizzarlo
                        findViewById(R.id.VideoView).setVisibility(View.VISIBLE);
                        findViewById(R.id.NuovoB).setVisibility(View.VISIBLE);
                        findViewById(R.id.GiraFotocameraB).setVisibility(View.GONE);
                        findViewById(R.id.ScattaRegistraB).setVisibility(View.GONE);
                        findViewById(R.id.CaricaMediaB).setVisibility(View.GONE);
                        findViewById(R.id.AnalizzaB).setVisibility(View.VISIBLE);
                        ((ImageButton) findViewById(R.id.ScattaRegistraB)).setColorFilter(Color.WHITE);
                    }
                }
            });

    // ANALISI DELL'IMMAGINE

    // Metodo per gestire l'importazione e l'inizializzazione di OpenCV in Android
    static {
        if(!OpenCVLoader.initDebug()) {
            Log.d("ERROR", "Unable to load OpenCV");
        }
        else {
            Log.d("SUCCESS", "OpenCV loaded");
        }
    }

    // Metodo per analizzare il flusso della fotocamera in modalità live
    @Override
    public void analyze(@NonNull ImageProxy image) {
        if(analisiAttiva && modalitaAttiva == MODALITA_LIVE)                                        // Se l'analisi live è attiva
        {
            Bitmap immagine = ((PreviewView) findViewById(R.id.PreviewView)).getBitmap();           // Bitmap dell'immagine visualizzata nella preview
            findViewById(R.id.PreviewView).setVisibility(View.GONE);
            findViewById(R.id.ImmagineView).setVisibility(View.VISIBLE);

            if(analisiAttivaLive)                                                                   // Se non c'è nessun thread che analizza l'immagine live
            {
                new Thread() {                                                                      // Thread utilizzato per effettuare l'analisi del video in background, visto che è un processo lungo ed eseguirlo in primo piano bloccherebbe l'app
                    public void run() {
                        analisiAttivaLive = false;                                                  // Disabilito momentaneamente l'analisi per non avere troppo overhead dai continui frame che arrivano dalla fotocamera

                        float sogliaMinima = sogliaVolti;                                           // Scelgo la soglia minima tra quella per la faccia e quella per il testo
                        if (sogliaTesti < sogliaVolti) {
                            sogliaMinima = sogliaVolti;
                        }
                        sogliaMinima /= 100;                                                        // Divido la soglia minima per 100 per ottenere un valore in percentuale

                        try {
                            Mat mat = new Mat();
                            Mat mat2 = new Mat();
                            TensorImage immagineTF = TensorImage.fromBitmap(immagine);              // Creo l'object detector per elaborare l'immagine secondo il modello TensorFlow Lite specificato
                            ObjectDetector detector;

                            if (tipoAnalisi == 0)                                                   // Si sceglie il modello da utilizzare sulla base del tipo di analisi voluta dall'utente (prestazioni o precisione)
                            {
                                detector = ObjectDetector.createFromFileAndOptions(MainActivity.this, "android0.tflite", ObjectDetector.ObjectDetectorOptions.builder().setMaxResults(numeroOggetti).setScoreThreshold(sogliaMinima).build());
                            } else {
                                detector = ObjectDetector.createFromFileAndOptions(MainActivity.this, "android2.tflite", ObjectDetector.ObjectDetectorOptions.builder().setMaxResults(numeroOggetti).setScoreThreshold(sogliaMinima).build());
                            }

                            List<Detection> risultati = detector.detect(immagineTF);                // Lista contenente tutti i riferimenti agli oggetti rilevati nell'immagine
                            ListIterator<Detection> iteratoreRisultati = risultati.listIterator();  // Iteratore della lista per poter elaborare volta per volta tutti gli oggetti della lista

                            if (!iteratoreRisultati.hasNext())                                      // Se non è stato rilevato nessun oggetto nell'immagine
                            {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ((ImageView) findViewById(R.id.ImmagineView)).setImageBitmap(immagine); // Mostro nella ImageView del risultato l'immagine originale
                                    }
                                });
                            } else {
                                Bitmap finale = Bitmap.createBitmap(immagine.getWidth(), immagine.getHeight(), Bitmap.Config.ARGB_8888);        // Bitmap che conterrà il risultato finale dell'analisi
                                Canvas canvas = new Canvas(finale);
                                canvas.drawBitmap(immagine, 0f, 0f, null);

                                while (iteratoreRisultati.hasNext()) {                                  // Fino a quando ci sono elementi nella lista
                                    Detection det = iteratoreRisultati.next();                          // Seleziono i dati dell'oggetto rilevato nell'immagine
                                    List<Category> categorie = det.getCategories();
                                    Category categoria = categorie.get(0);                              // Ottengo la categoria (faccia o testo) a cui l'oggetto rilevato ha più probabilità di appartenere
                                    String labelCategoria = categoria.getLabel();                       // Ottengo il nome della categoria di appartenenza: faccia o testo
                                    int punteggioCategoria = Math.round(categoria.getScore() * 100);    // Ottengo la percentuale di affidabilità di rilevamento

                                    // Visualizzo solo gli oggetti rilevati che hanno una soglia di affidabilità superiore alla soglia minima e alla soglia specifica per quella categoria di oggetto
                                    if ((labelCategoria.equals("faccia") && rilevVolti && punteggioCategoria >= sogliaVolti) || (labelCategoria.equals("testo") && rilevTesti && punteggioCategoria >= sogliaTesti)) {
                                        RectF boxF = det.getBoundingBox();                              // Ottengo le coordinate della box che delinea lo spazio in cui è presente l'oggetto rilevato
                                        Rect box = new Rect();
                                        boxF.roundOut(box);

                                        // Controllo per far sì che x e y siano maggiori di 0 e per far sì che la dimensione del rettangolo non superi i limite dell'immagine
                                        int x = box.left >= 0 ? box.left : 0;
                                        int y = box.top >= 0 ? box.top : 0;
                                        int width = (x + box.width()) <= immagine.getWidth() ? box.width() : (immagine.getWidth() - x);
                                        int height = (y + box.height()) <= immagine.getHeight() ? box.height() : (immagine.getHeight() - y);

                                        Bitmap tagliata = Bitmap.createBitmap(immagine, x, y, width, height);                                       // Ritaglio la parte dell'immagine che contiene l'oggetto identificato
                                        Utils.bitmapToMat(tagliata, mat);                                                                           // Trasformo l'immagine in Mat
                                        Imgproc.resize(mat, mat2, new Size(PIXELATION_SIZE, PIXELATION_SIZE), 0, 0, Imgproc.INTER_LINEAR);    // Effettua una interpolazione per creare la matrice di pixel
                                        Imgproc.resize(mat2, mat, mat.size(), 0, 0, Imgproc.INTER_NEAREST);                                   // Effettua una nuova interpolazione per adattare la matrice di pixel all'immagine originale
                                        Utils.matToBitmap(mat, tagliata);                                                                           // Traforma la Mat in immagine

                                        canvas.drawBitmap(tagliata, x, y, null);               // Sull'immagine originale, disegno l'immagine tagliata pixellata
                                    }

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            ((ImageView) findViewById(R.id.ImmagineView)).setImageBitmap(finale);       // Mostro l'immagine completamente analizzata
                                        }
                                    });
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        analisiAttivaLive = true;                                                   // Riattivo l'analisi nuovamente per processare la prossima immagine acquisita dalla fotocamera
                    }
                }.start();
            }
        }
        image.close();
    }

    // Metodo per analizzare l'immagine scattata o caricata dalla galleria in modalità foto
    private void analisiFoto()
    {
        float sogliaMinima = sogliaVolti;                                                           // Scelgo la soglia minima tra quella per la faccia e quella per il testo
        if(sogliaTesti < sogliaVolti)
        {
            sogliaMinima = sogliaVolti;
        }
        sogliaMinima /= 100;                                                                        // Divido la soglia minima per 100 per ottenere un valore in percentuale

        try {
            Bitmap immagine = imgPhotoMode;                                                         // Ottengo il bitmap dall'immagine scattata o caricata dalla galleria
            Mat mat = new Mat();
            Mat mat2 = new Mat();
            TensorImage immagineTF = TensorImage.fromBitmap(immagine);                              // Creo l'object detector per elaborare l'immagine secondo il modello TensorFlow Lite specificato
            ObjectDetector detector;

            if(tipoAnalisi == 0)                                                                    // Si sceglie il modello da utilizzare sulla base del tipo di analisi voluta dall'utente (prestazioni o precisione)
            {
                detector = ObjectDetector.createFromFileAndOptions(MainActivity.this, "android0.tflite", ObjectDetector.ObjectDetectorOptions.builder().setMaxResults(numeroOggetti).setScoreThreshold(sogliaMinima).build());
            } else {
                detector = ObjectDetector.createFromFileAndOptions(MainActivity.this, "android2.tflite", ObjectDetector.ObjectDetectorOptions.builder().setMaxResults(numeroOggetti).setScoreThreshold(sogliaMinima).build());
            }

            List<Detection> risultati = detector.detect(immagineTF);                                // Lista contenente tutti i riferimenti agli oggetti rilevati nell'immagine
            ListIterator<Detection> iteratoreRisultati = risultati.listIterator();                  // Iteratore della lista per poter elaborare volta per volta tutti gli oggetti della lista

            if(iteratoreRisultati.hasNext())                                                        // Se è stato rilevato almeno un oggetto nell'immagine
            {
                Bitmap finale = Bitmap.createBitmap(immagine.getWidth(), immagine.getHeight(), Bitmap.Config.ARGB_8888);        // Bitmap che conterrà il risultato finale dell'analisi
                Canvas canvas = new Canvas(finale);
                canvas.drawBitmap(immagine, 0f, 0f, null);

                while(iteratoreRisultati.hasNext()) {                                               // Fino a quando ci sono elementi nella lista
                    Detection det = iteratoreRisultati.next();                                      // Seleziono i dati dell'oggetto rilevato nell'immagine
                    List<Category> categorie = det.getCategories();
                    Category categoria = categorie.get(0);                                          // Ottengo la categoria (faccia o testo) a cui l'oggetto rilevato ha più probabilità di appartenere
                    String labelCategoria = categoria.getLabel();                                   // Ottengo il nome della categoria di appartenenza: faccia o testo
                    int punteggioCategoria = Math.round(categoria.getScore() * 100);                // Ottengo la percentuale di affidabilità di rilevamento

                    // Visualizzo solo gli oggetti rilevati che hanno una soglia di affidabilità superiore alla soglia minima e alla soglia specifica per quella categoria di oggetto
                    if ((labelCategoria.equals("faccia") && rilevVolti && punteggioCategoria >= sogliaVolti) || (labelCategoria.equals("testo") && rilevTesti && punteggioCategoria >= sogliaTesti))
                    {
                        RectF boxF = det.getBoundingBox();                                          // Ottengo le coordinate della box che delinea lo spazio in cui è presente l'oggetto rilevato
                        Rect box = new Rect();
                        boxF.roundOut(box);

                        // Controllo per far sì che x e y siano maggiori di 0 e per far sì che la dimensione del rettangolo non superi i limite dell'immagine
                        int x = box.left >= 0 ? box.left : 0;
                        int y = box.top >= 0 ? box.top : 0;
                        int width = (x + box.width()) <= immagine.getWidth() ? box.width() : (immagine.getWidth()-x);
                        int height = (y + box.height()) <= immagine.getHeight() ? box.height() : (immagine.getHeight()-y);

                        Bitmap tagliata = Bitmap.createBitmap(immagine, x, y, width, height);                                       // Ritaglio la parte dell'immagine che contiene l'oggetto identificato
                        Utils.bitmapToMat(tagliata, mat);                                                                           // Trasformo l'immagine in Mat
                        Imgproc.resize(mat, mat2, new Size(PIXELATION_SIZE, PIXELATION_SIZE), 0, 0, Imgproc.INTER_LINEAR);    // Effettua una interpolazione per creare la matrice di pixel
                        Imgproc.resize(mat2, mat, mat.size(), 0, 0, Imgproc.INTER_NEAREST);                                   // Effettua una nuova interpolazione per adattare la matrice di pixel all'immagine originale
                        Utils.matToBitmap(mat, tagliata);                                                                           // Traforma la Mat in immagine

                        canvas.drawBitmap(tagliata, x, y, null);                               // Sull'immagine originale, disegno l'immagine tagliata pixellata
                    }

                    ((ImageView) findViewById(R.id.ImmagineView)).setImageBitmap(finale);           // Mostro l'immagine completamente analizzata
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        findViewById(R.id.AnalizzaB).setVisibility(View.GONE);                                          // Nascondo il button per analizzare l'immagine, per evitare di rianalizzare la stessa immagine nuovamente
        Toast.makeText(getApplicationContext(), "Analisi terminata", Toast.LENGTH_LONG).show();     // Mostro l'avviso che indica la terminazione dell'analisi della foto
    }

    // Metodo per analizzare il video registrato o caricato dalla galleria in modalità video
    private void analisiVideo() {
        threadAnalisiVideo = new Thread() {                                                         // Thread utilizzato per effettuare l'analisi del video in background, visto che è un processo lungo ed eseguirlo in primo piano bloccherebbe l'app
            public void run() {
                String pathNuovoVideo = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/SISDIG_Elaborato.mp4";     // Nome del file che conterrà il video analizzato
                MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                SeekableByteChannel out = null;
                AndroidSequenceEncoder encoder = null;
                int maxFrame = (Math.round(((VideoView) findViewById(R.id.VideoView)).getDuration() / 100.0F) * 3);     // Calcolo il numero massimo di frame che il video potrebbe contenere
                int frame = 0;                                                                                          // Numero del frame che si sta elaborando

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView) findViewById(R.id.VideoFrameElab)).setText("");                 // Mostro l'avviso che indica quale frame è in elaborazione
                        findViewById(R.id.VideoFrameElab).setVisibility(View.VISIBLE);
                        findViewById(R.id.AnalizzaB).setVisibility(View.GONE);
                    }
                });

                float sogliaMinima = sogliaVolti;                                                   // Scelgo la soglia minima tra quella per la faccia e quella per il testo
                if(sogliaTesti < sogliaVolti)
                {
                    sogliaMinima = sogliaVolti;
                }
                sogliaMinima /= 100;                                                                // Divido la soglia minima per 100 per ottenere un valore in percentuale

                try {
                    mediaMetadataRetriever.setDataSource(MainActivity.this, uriVideo);       // Imposto il video sorgente da cui prendere i frame da analizzare
                    out = NIOUtils.writableFileChannel(pathNuovoVideo);
                    encoder = new AndroidSequenceEncoder(out, Rational.R(30, 1));          // Creo un canale di output che servirà per mettere insieme il nuovo video analizzato (30 FPS)

                    while(frame < maxFrame) {                                                       // Fino a quando ci sono frame da elaborare
                        Bitmap immagine = mediaMetadataRetriever.getFrameAtIndex(frame);            // Ottengo il bitmap associato al frame sotto analisi
                        frame += 1;                                                                 // Incremento il numero del frame, puntando al prossimo frame da analizzare
                        Mat mat = new Mat();
                        Mat mat2 = new Mat();
                        TensorImage immagineTF = TensorImage.fromBitmap(immagine);                  // Creo l'object detector per elaborare l'immagine secondo il modello TensorFlow Lite specificato
                        ObjectDetector detector;

                        if(tipoAnalisi == 0)                                                        // Si sceglie il modello da utilizzare sulla base del tipo di analisi voluta dall'utente (prestazioni o precisione)
                        {
                            detector = ObjectDetector.createFromFileAndOptions(MainActivity.this, "android0.tflite", ObjectDetector.ObjectDetectorOptions.builder().setMaxResults(numeroOggetti).setScoreThreshold(sogliaMinima).build());
                        } else {
                            detector = ObjectDetector.createFromFileAndOptions(MainActivity.this, "android2.tflite", ObjectDetector.ObjectDetectorOptions.builder().setMaxResults(numeroOggetti).setScoreThreshold(sogliaMinima).build());
                        }

                        List<Detection> risultati = detector.detect(immagineTF);                    // Lista contenente tutti i riferimenti agli oggetti rilevati nell'immagine
                        ListIterator<Detection> iteratoreRisultati = risultati.listIterator();      // Iteratore della lista per poter elaborare volta per volta tutti gli oggetti della lista

                        if(!iteratoreRisultati.hasNext())                                           // Se non è stato rilevato nessun oggetto nell'immagine
                        {
                            encoder.encodeImage(immagine);                                          // Salvo come frame del video analizzato lo stesso frame del video originale
                        } else {
                            Bitmap finale = Bitmap.createBitmap(immagine.getWidth(), immagine.getHeight(), Bitmap.Config.ARGB_8888);        // Bitmap che conterrà il risultato finale dell'analisi
                            Canvas canvas = new Canvas(finale);
                            canvas.drawBitmap(immagine, 0f, 0f, null);

                            while (iteratoreRisultati.hasNext()) {                                  // Fino a quando ci sono elementi nella lista
                                Detection det = iteratoreRisultati.next();                          // Seleziono i dati dell'oggetto rilevato nell'immagine
                                List<Category> categorie = det.getCategories();
                                Category categoria = categorie.get(0);                              // Ottengo la categoria (faccia o testo) a cui l'oggetto rilevato ha più probabilità di appartenere
                                String labelCategoria = categoria.getLabel();                       // Ottengo il nome della categoria di appartenenza: faccia o testo
                                int punteggioCategoria = Math.round(categoria.getScore() * 100);    // Ottengo la percentuale di affidabilità di rilevamento

                                // Visualizzo solo gli oggetti rilevati che hanno una soglia di affidabilità superiore alla soglia minima e alla soglia specifica per quella categoria di oggetto
                                if ((labelCategoria.equals("faccia") && rilevVolti && punteggioCategoria >= sogliaVolti) || (labelCategoria.equals("testo") && rilevTesti && punteggioCategoria >= sogliaTesti))
                                {
                                    RectF boxF = det.getBoundingBox();                                          // Ottengo le coordinate della box che delinea lo spazio in cui è presente l'oggetto rilevato
                                    Rect box = new Rect();
                                    boxF.roundOut(box);

                                    // Controllo per far sì che x e y siano maggiori di 0 e per far sì che la dimensione del rettangolo non superi i limite dell'immagine
                                    int x = box.left >= 0 ? box.left : 0;
                                    int y = box.top >= 0 ? box.top : 0;
                                    int width = (x + box.width()) <= immagine.getWidth() ? box.width() : (immagine.getWidth()-x);
                                    int height = (y + box.height()) <= immagine.getHeight() ? box.height() : (immagine.getHeight()-y);

                                    Bitmap tagliata = Bitmap.createBitmap(immagine, x, y, width, height);                                       // Ritaglio la parte dell'immagine del frame che contiene l'oggetto identificato
                                    Utils.bitmapToMat(tagliata, mat);                                                                           // Trasformo l'immagine tagliata in Mat
                                    Imgproc.resize(mat, mat2, new Size(PIXELATION_SIZE, PIXELATION_SIZE), 0, 0, Imgproc.INTER_LINEAR);    // Effettua una interpolazione per creare la matrice di pixel
                                    Imgproc.resize(mat2, mat, mat.size(), 0, 0, Imgproc.INTER_NEAREST);                                   // Effettua una nuova interpolazione per adattare la matrice di pixel al'immagine del frame originale
                                    Utils.matToBitmap(mat, tagliata);                                                                           // Traforma la Mat in immagine

                                    canvas.drawBitmap(tagliata, x, y, null);                   // Sull'immagine del frame originale, disegno l'immagine tagliata pixellata
                                }
                            }

                            encoder.encodeImage(finale);                                            // Salvo come frame del video analizzato il frame elaborato
                        }

                        int finalFrame = frame;
                        runOnUiThread(new Runnable() {                                              // Mostro l'avanzamento nell'elaborazione dei frame
                            @Override
                            public void run() {
                                ((TextView) findViewById(R.id.VideoFrameElab)).setText(String.valueOf(finalFrame) + "/" + maxFrame + " FRAME ELABORATI");
                            }
                        });

                        Log.d("ProcessNewVideo", String.valueOf(frame) + " FRAME ELABORATI");
                    }

                    // Quando l'elaborazione dei frame finisce
                    try {
                        encoder.finish();                                                           // Chiudo il canale output di scrittura frame nel video analizzato
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    mediaMetadataRetriever.release();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            findViewById(R.id.VideoFrameElab).setVisibility(View.GONE);
                            MediaController mediaController = new MediaController(MainActivity.this);       // Controller per gestire la riproduzione del video
                            ((VideoView) findViewById(R.id.VideoView)).setVideoPath(pathNuovoVideo);               // Imposto il video analizzato nella VideoView
                            ((VideoView) findViewById(R.id.VideoView)).setMediaController(mediaController);        // Collego il media controller al video
                            mediaController.setAnchorView(((VideoView) findViewById(R.id.VideoView)));
                            ((VideoView) findViewById(R.id.VideoView)).start();                                    // Avvio il video

                            Toast.makeText(getApplicationContext(), "Analisi terminata", Toast.LENGTH_LONG).show();     // Mostro l'avviso che indica la terminazione dell'analisi del video
                        }
                    });
                } catch(IllegalArgumentException e) {                                               // Nel caso i frame che componevano il video erano meno di quelli previsti (frame totali << maxFrame)
                    // Quando l'elaborazione dei frame finisce
                    try {
                        encoder.finish();                                                           // Chiudo il canale output di scrittura frame nel video analizzato
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    mediaMetadataRetriever.release();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            findViewById(R.id.VideoFrameElab).setVisibility(View.GONE);
                            MediaController mediaController = new MediaController(MainActivity.this);       // Controller per gestire la riproduzione del video
                            ((VideoView) findViewById(R.id.VideoView)).setVideoPath(pathNuovoVideo);               // Imposto il video analizzato nella VideoView
                            ((VideoView) findViewById(R.id.VideoView)).setMediaController(mediaController);        // Collego il media controller al video
                            mediaController.setAnchorView(((VideoView) findViewById(R.id.VideoView)));
                            ((VideoView) findViewById(R.id.VideoView)).start();                                    // Avvio il video

                            Toast.makeText(getApplicationContext(), "Analisi terminata", Toast.LENGTH_LONG).show();     // Mostro l'avviso che indica la terminazione dell'analisi del video
                        }
                    });
                }
                catch (Exception e) {
                    threadAnalisiVideo.interrupt();
                    Log.d("ProcessNewVideo", "PROBLEMI " + e.getMessage());
                } finally {
                    NIOUtils.closeQuietly(out);
                }

                threadAnalisiVideo = null;                                                          // Resetto il riferimento al thread che analizza il video, visto che si è arrivati alla sua fine
            }
        };
        threadAnalisiVideo.start();                                                                 // Avvio il thread creato
    }

    // CAMBIO MODALITA'

    // Metodo utilizzato per passare da una modalità ad un'altra
    private void cambioModalita(int nuovaModalita)
    {
        analisiAttiva = false;                                                                      // Disattivo eventuali analisi in corso
        analisiAttivaLive = false;                                                                  // Disattivo nuove analisi di immagini live
        modalitaAttiva = nuovaModalita;                                                             // Cambio modalità
        registrandoVideo = false;                                                                   // Fermo eventuali registrazioni video

        if(threadAnalisiVideo != null) {                                                            // Se in background è in esecuzione il thread per analizzare il video
            threadAnalisiVideo.interrupt();                                                         // Interrompo il thread di analisi video
        }

        try {
            avviaFotocamera(provider.get());                                                        // Riavvio la fotocamera
        } catch(Exception e) {
            e.printStackTrace();
        }

        switch(modalitaAttiva)                                                                      // La schermata che vede l'utente cambia sulla base della modalità scelta
        {
            case MODALITA_LIVE:
                ((Button) findViewById(R.id.LiveModeB)).setTextColor(Color.parseColor(coloreAttivo));
                ((Button) findViewById(R.id.PhotoModeB)).setTextColor(Color.WHITE);
                ((Button) findViewById(R.id.VideoModeB)).setTextColor(Color.WHITE);
                findViewById(R.id.PreviewView).setVisibility(View.VISIBLE);
                findViewById(R.id.ImmagineView).setVisibility(View.GONE);
                findViewById(R.id.VideoView).setVisibility(View.GONE);
                findViewById(R.id.VideoFrameElab).setVisibility(View.GONE);
                findViewById(R.id.NuovoB).setVisibility(View.GONE);
                findViewById(R.id.GiraFotocameraB).setVisibility(View.VISIBLE);
                findViewById(R.id.ScattaRegistraB).setVisibility(View.GONE);
                findViewById(R.id.CaricaMediaB).setVisibility(View.GONE);
                findViewById(R.id.AnalizzaB).setVisibility(View.VISIBLE);
                ((ImageButton) findViewById(R.id.AnalizzaB)).setColorFilter(Color.WHITE);
                break;

            case MODALITA_FOTO:
                ((Button) findViewById(R.id.LiveModeB)).setTextColor(Color.WHITE);
                ((Button) findViewById(R.id.PhotoModeB)).setTextColor(Color.parseColor(coloreAttivo));
                ((Button) findViewById(R.id.VideoModeB)).setTextColor(Color.WHITE);
                findViewById(R.id.PreviewView).setVisibility(View.VISIBLE);
                findViewById(R.id.ImmagineView).setVisibility(View.GONE);
                findViewById(R.id.VideoView).setVisibility(View.GONE);
                findViewById(R.id.VideoFrameElab).setVisibility(View.GONE);
                findViewById(R.id.NuovoB).setVisibility(View.GONE);
                findViewById(R.id.GiraFotocameraB).setVisibility(View.VISIBLE);
                findViewById(R.id.ScattaRegistraB).setVisibility(View.VISIBLE);
                findViewById(R.id.CaricaMediaB).setVisibility(View.VISIBLE);
                findViewById(R.id.AnalizzaB).setVisibility(View.GONE);
                ((ImageButton) findViewById(R.id.ScattaRegistraB)).setColorFilter(Color.WHITE);
                ((ImageButton) findViewById(R.id.AnalizzaB)).setColorFilter(Color.WHITE);
                break;

            case MODALITA_VIDEO:
                ((Button) findViewById(R.id.LiveModeB)).setTextColor(Color.WHITE);
                ((Button) findViewById(R.id.PhotoModeB)).setTextColor(Color.WHITE);
                ((Button) findViewById(R.id.VideoModeB)).setTextColor(Color.parseColor(coloreAttivo));
                findViewById(R.id.PreviewView).setVisibility(View.VISIBLE);
                findViewById(R.id.ImmagineView).setVisibility(View.GONE);
                findViewById(R.id.VideoView).setVisibility(View.GONE);
                findViewById(R.id.VideoFrameElab).setVisibility(View.GONE);
                findViewById(R.id.NuovoB).setVisibility(View.GONE);
                findViewById(R.id.GiraFotocameraB).setVisibility(View.VISIBLE);
                findViewById(R.id.ScattaRegistraB).setVisibility(View.VISIBLE);
                findViewById(R.id.CaricaMediaB).setVisibility(View.VISIBLE);
                findViewById(R.id.AnalizzaB).setVisibility(View.GONE);
                ((ImageButton) findViewById(R.id.ScattaRegistraB)).setColorFilter(Color.WHITE);
                ((ImageButton) findViewById(R.id.AnalizzaB)).setColorFilter(Color.WHITE);
                break;
        }
    }
}