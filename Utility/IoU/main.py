import os
import xml.etree.ElementTree as ET

def elencoFile():                                                                                                       # Creazione elenco contenente tutti i file usati per il test
    numeroFile = 0                                                                                                      # Numero di file elaborati
    dirname = "test/"                                                                                                   # Path directory contenente i file usati per il test
    with open('elencoFile.txt', 'w') as file:                                                                           # Apro in scrittura il file in cui salvare i nomi dei file usati per il test
        for filename in os.listdir(dirname):                                                                            # Per ogni file contenuto nella directory test
            if (filename.endswith(".jpg")):                                                                             # Se il file ha estensione .jpg
                file.write(filename)                                                                                    # Salvo il nome del file trovato
                file.write("\n")
                numeroFile += 1                                                                                         # Incremento il numero di file elaborati

    print("Numero file: " + str(numeroFile))                                                                            # Stampo il numero di file elaborati

def calcoloIoU():                                                                                                       # Calcolo dei vari IoU
    IoU_totale = 0.0                                                                                                    # IoU di tutte le immagini di test considerate
    Num_totale = 0                                                                                                      # Numero di immagini utilizzate per il calcolo del IoU_totale
    IoU_visi = 0.0                                                                                                      # IoU di tutte le immagini di test contenenti visi
    Num_visi = 0                                                                                                        # Numero di immagini utilizzate per il calcolo del IoU_visi
    IoU_testi = 0.0                                                                                                     # IoU di tutte le immagini di test contenenti testi
    Num_testi = 0                                                                                                       # Numero di immagini utilizzate per il calcolo del IoU_testi

    dirname = "test/"                                                                                                   # Path directory contenente i file usati per il test
    with open('elencoFile.txt', 'r') as file:                                                                           # Apro in lettura il file contenente i nomi dei file usati per il test
        while (riga := file.readline()):                                                                                # Per ogni immagine di test
            filename = dirname + riga.replace("\n", "")                                                                 # Ottengo il path completo dell'immagine di test considerata

            alberoXML = ET.parse(filename.replace(".jpg", ".xml"))                                                      # Apro il file .xml associato all'immagine considerata
            radice = alberoXML.getroot()                                                                                # Ottengo il tag root del file .xml

            boxOriginarie = []                                                                                          # Array contenente le box definite dall'utente
            for oggetti in radice.findall("object"):                                                                    # Per ogni oggetto nella scena
                xmin = int(oggetti[4].find('xmin').text)
                ymin = int(oggetti[4].find('ymin').text)
                xmax = int(oggetti[4].find('xmax').text)
                ymax = int(oggetti[4].find('ymax').text)
                boxOriginarie.append([xmin, ymin, xmax, ymax])                                                          # Ottengo e salvo i punti della box

            trovata = 0                                                                                                 # 0 = Nessun oggetto rielvato | 1 = Almeno un oggetto rilevato
            boxTrovate = []                                                                                             # Array contenente le box rilevate
            with open(filename.replace(".jpg", ".txt"), 'r') as fileTesto:                                              # Apro in lettura il file contenente i dati delle box rilevate associate all'immagine sotto analisi
                while (rigaTesto := fileTesto.readline()):                                                              # Leggo i dati di una singola box
                        trovata = 1                                                                                     # Indico che è stato rilevato almeno un oggetto
                        boxT = rigaTesto.split(" ")
                        boxTrovate.append([int(boxT[0]), int(boxT[1]), int(boxT[2]), int(boxT[3])])                     # Ottengo e salvo i punti della box
            fileTesto.close()

            if(trovata == 1):                                                                                           # Se ho trovato almeno una box
                IoU_immagine = []                                                                                       # Array che conterrà gli IoU associati all'immagine sotto analisi
                for boxO in boxOriginarie:                                                                              # Per ogni box definita dall'utente
                    for boxT in boxTrovate:                                                                             # Per ogni box rilevata
                        IoU_immagine.append(bb_intersection_over_union(boxO, boxT))                                     # Salvo l'IoU associato alle due box selezionate
                IoU_immagine.sort(reverse=True)                                                                         # Ordino tutti i valori di IoU in ordine decrescente (posizione 0 -> IoU migliore)

                if(filename.startswith("test/COCO")):                                                                   # Se sto analizzando un'immagine contenente testi
                    IoU_testi = IoU_testi + IoU_immagine[0]
                    Num_testi = Num_testi + 1
                else:                                                                                                   # Se sto analizzando un'immagine contenente volti
                    IoU_visi = IoU_visi + IoU_immagine[0]
                    Num_visi = Num_visi + 1
                IoU_totale = IoU_totale + IoU_immagine[0]
                Num_totale = Num_totale + 1

    IoU_totale = IoU_totale / Num_totale                                                                                # Calcolo il valore del IoU sia per le immagini di visi sia per quelle di testi
    IoU_testi = IoU_testi / Num_testi                                                                                   # Calcolo il valore del IoU per le immagini di testi
    IoU_visi = IoU_visi / Num_visi                                                                                      # Calcolo il valore del IoU per le immagini di visi

    print("IoU: " + str(IoU_totale) + " - IoU visi: " + str(IoU_visi) + " - IoU testi: " + str(IoU_testi))              # Mostro il risultato

def bb_intersection_over_union(boxA, boxB):                                                                             # Calcolo del valore di IoU tra due box
    xA = max(boxA[0], boxB[0])
    yA = max(boxA[1], boxB[1])
    xB = min(boxA[2], boxB[2])
    yB = min(boxA[3], boxB[3])

    interArea = max(0, xB - xA + 1) * max(0, yB - yA + 1)                                                               # Calcolo l'area di intersezione dei rettanfoli
    boxAArea = (boxA[2] - boxA[0] + 1) * (boxA[3] - boxA[1] + 1)                                                        # Calcolo l'area della prima box
    boxBArea = (boxB[2] - boxB[0] + 1) * (boxB[3] - boxB[1] + 1)                                                        # Calcolo l'area della seconda box
    iou = interArea / float(boxAArea + boxBArea - interArea)                                                            # Calcolo il rapporto tra l'area di intersezione e l'area di unione delle due box

    return iou                                                                                                          # Restituisco il valore calcolato

if __name__ == '__main__':
    calcoloIoU()