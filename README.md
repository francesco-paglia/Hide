# Hide
Il progetto **HIDE** nasce con l’obiettivo di creare un’**applicazione Android** in grado di rilevare e oscurare i volti e le scritte all’interno di immagini e video. 

Nello specifico, l'applicazione prevede tre modalità di utilizzo:</br>
**1. Live:** il rilevamento e l’oscuramento avvengono in tempo reale, analizzando costantemente il flusso di immagini provenienti dalla fotocamera.</br>
**2. Foto:** il rilevamento e l’oscuramento avvengono su un’immagine caricata dalla galleria o su una foto appena scattata.</br>
**3. Video:** il rilevamento e l’oscuramento avvengono su un video appena registrato o caricato dalla galleria.

Per il rilevamento di visi e testi sono stati implementati dei modelli TensorFlow Lite in grado di riconoscere due classi di oggetti:</br>
**1. Faccia:** riconoscere il volto di una persona, persino da angolazioni diverse.</br>
**2. Testo:** riconoscere scritte differenti per grandezza, font e orientamento.

L'addestramento viene effettuato con la libreria **TensorFlow Lite Model Maker**, in grado di addestrare una rete neurale grazie alla tecnica del **transfer learning**: riconversione di una rete preaddestrata. In questo caso, la rete preaddestrata ha un'architettura **EfficentDet-Lite**, ottimizzata per eseguire object detection in maniera efficiente e scalabile su sistemi embedded. 

Per maggiori dettagli sul progetto, consultare la **relazione**.
