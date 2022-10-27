import os
import json
from PIL import Image

def fromJSONtoXML_COCO(annotazioni, train_dir, val_dir):                                                                # Convertire le annotazioni del dataset COCO-Text da JSON a XML
    numFileConvertiti = 0                                                                                               # Numero di file convertiti
    fileAnn = open(annotazioni, "r")                                                                                    # Apro il file contenente le annotazioni
    data = json.load(fileAnn)                                                                                           # Leggo i dati json

    for idImg in data['imgs']:                                                                                          # Per ogni immagine annotata
        oggetti = []                                                                                                    # Contiene le coordinate del rettangolo che delinea una scritta
        numOggetti = 0                                                                                                  # Numero di scritte nell'immagine
        width = data['imgs'][idImg]['width']
        height = data['imgs'][idImg]['height']
        nomeImg = data['imgs'][idImg]['file_name']
        folder = data['imgs'][idImg]['set']                                                                             # Directory in cui si trova l'immagine -> train o validation
        directoryImg = train_dir                                                                                        # Questa variabile conterrà il path completo della directory che contiene l'immagine
        if folder=="val":
            directoryImg = val_dir
            folder = "validation"

        for idAnn in data['anns']:                                                                                      # Per ogni annotazione presente nel file delle annotazioni
            if data['anns'][idAnn]['image_id']==int(idImg):                                                             # Confronto l'id dell'immagine dell'annotazione per capire se è lo stesso dell'immagine sotto analisi
                numOggetti+=1
                coordinate = data['anns'][idAnn]['bbox']                                                                # Leggo le coordinate del rettangolo che delinea una scritta
                oggetti.append([str(int(coordinate[0])), str(int(coordinate[1])), str(int(coordinate[0])+int(coordinate[2])), str(int(coordinate[1])+int(coordinate[3]))])

        if numOggetti>0:                                                                                                # Se l'immagine sotto analisi contiene delle scritte
            fileXML = open(directoryImg + "\\" + nomeImg.replace(".jpg", ".xml"), "w")                                  # Creo il file XML che conterrà i dati raccolti dal file JSON, organizzandoli secondo il formato Pascal VOC
            fileXML.write("<annotation verified=\"yes\">")
            fileXML.write("\n\t<folder>" + folder + "</folder>")
            fileXML.write("\n\t<filename>" + nomeImg + "</filename>")
            fileXML.write("\n\t<path>" + directoryImg + "\\" + nomeImg + "</path>")
            fileXML.write("\n\t<source>\n\t\t<database>Unknown</database>\n\t</source>")
            fileXML.write("\n\t<size>\n\t\t<width>"+str(width)+"</width>\n\t\t<height>"+str(height)+"</height>\n\t\t<depth>3</depth>\n\t</size>")
            fileXML.write("\n\t<segmented>0</segmented>")

            for i in range(numOggetti):
                fileXML.write(
                    "\n\t<object>\n\t\t<name>testo</name>\n\t\t<pose>Unspecified</pose>\n\t\t<truncated>0</truncated>\n\t\t<difficult>0</difficult>\n\t\t<bndbox>\n\t\t\t<xmin>" +
                    oggetti[i][0] + "</xmin>\n\t\t\t<ymin>" + oggetti[i][1] + "</ymin>\n\t\t\t<xmax>" + oggetti[i][
                        2] + "</xmax>\n\t\t\t<ymax>" + oggetti[i][3] + "</ymax>\n\t\t</bndbox>\n\t</object>")

            fileXML.write("\n</annotation>")
            fileXML.close()
            numFileConvertiti += 1                                                                                      # Incremento il numero di file convertiti

    fileAnn.close()
    print("Ho trasformato " + str(numFileConvertiti) + " file!\n")

def fromTXTtoXML_WIDERFACE(path_immagini, folder, annotazioni):                                                         # Convertire le annotazioni del dataset WIDER-FACE da TXT a XML
    numFileConvertiti = 0                                                                                               # Numero di file convertiti
    stato = 0                                                                                                           # 0 -> Leggo nome file - 1 -> Leggo numero oggetti - 2 -> Leggo dati faccia
    nomeImg = ""
    directoryImg = ""
    oggetti = []                                                                                                        # Contiene le coordinate del rettangolo che delinea una faccia
    numOggetti = 0                                                                                                      # Numero di facce nell'immagine
    os.chdir(path_immagini)

    fileAnn = open(annotazioni, "r")                                                                                    # Apro il file contenente le annotazioni
    rigaAnn = fileAnn.readline()                                                                                        # Lettura prima riga
    while rigaAnn:                                                                                                      # Fino a quando ci sono righe da leggere
        if stato==0:                                                                                                    # Se sono alla riga con il path dell'immagine
            directoryImg = rigaAnn.split("/")[0]                                                                        # Leggo il nome della directory contenente l'immagine
            nomeImg = rigaAnn.split("/")[1].replace("\n", "")                                                           # Leggo il nome dell'immagine
            stato = 1
            rigaAnn = fileAnn.readline()
        elif stato==1:                                                                                                  # Se sono alla riga che contiene il numero di facce presenti nell'immagine
            numOggetti = int(rigaAnn)                                                                                   # Leggo il numero di facce presenti
            oggetti = []
            stato = 2
            rigaAnn = fileAnn.readline()
        elif stato == 2 and numOggetti==0:                                                                              # Se sono alle righe contenenti i dati sulle coordinate delle facce - Numero delle facce nell'immagine uguale a 0
            rigaAnn = fileAnn.readline()
            stato = 0
            numFileConvertiti += 1
        elif stato==2 and numOggetti>0:                                                                                 # Se sono alle righe contenenti i dati sulle coordinate delle facce - Numero delle facce nell'immagine maggiore di 0
            for i in range(numOggetti):
                coordinate = rigaAnn.split()                                                                            # Leggo le coordinate del rettangolo che delinea una faccia
                oggetti.append([str(int(coordinate[0])), str(int(coordinate[1])), str(int(coordinate[0])+int(coordinate[2])), str(int(coordinate[1])+int(coordinate[3]))])
                rigaAnn = fileAnn.readline()

            img = Image.open(directoryFoto+"\\"+nomeFoto)                                                               # Apro l'immagine per ottenere la sua lunghezza e altezza
            width = img.width
            height = img.height
            img.close()

            fileXML = open(directoryImg+"\\"+nomeImg.replace(".jpg", ".xml"), "w")                                      # Creo il file XML che conterrà i dati raccolti dal file TXT, organizzandoli secondo il formato Pascal VOC
            fileXML.write("<annotation verified=\"yes\">")
            fileXML.write("\n\t<folder>" + folder + "</folder>")
            fileXML.write("\n\t<filename>" + nomeImg + "</filename>")
            fileXML.write("\n\t<path>" + path_immagini + "\\" + nomeImg + "</path>")
            fileXML.write("\n\t<source>\n\t\t<database>Unknown</database>\n\t</source>")
            fileXML.write("\n\t<size>\n\t\t<width>" + str(width) + "</width>\n\t\t<height>" + str(height) + "</height>\n\t\t<depth>3</depth>\n\t</size>")
            fileXML.write("\n\t<segmented>0</segmented>")

            for i in range(numOggetti):
                fileXML.write(
                    "\n\t<object>\n\t\t<name>faccia</name>\n\t\t<pose>Unspecified</pose>\n\t\t<truncated>0</truncated>\n\t\t<difficult>0</difficult>\n\t\t<bndbox>\n\t\t\t<xmin>" +
                    oggetti[i][0] + "</xmin>\n\t\t\t<ymin>" + oggetti[i][1] + "</ymin>\n\t\t\t<xmax>" + oggetti[i][
                        2] + "</xmax>\n\t\t\t<ymax>" + oggetti[i][3] + "</ymax>\n\t\t</bndbox>\n\t</object>")

            fileXML.write("\n</annotation>")
            fileXML.close()

            stato=0
            numFileConvertiti+=1
            print(str(numFileConverted)+" - "+dirFoto+"\\"+nomeFoto+"\n")

    fileAnn.close()
    print("Ho trasformato " + str(numFileConvertiti) + " file!\n")


def deleteFileNoXML(path):                                                                                              # Eliminare tutte le immagini che non hanno il relativo file XML
    os.chdir(path)                                                                                                      # Mi sposto nella directory contenente le immagini
    for filename in os.listdir(path):                                                                                   # Per ogni file nella directory
        if filename.endswith(".jpg"):                                                                                   # Controllo che il file sia un'immagine
            if not os.path.exists(filename.replace(".jpg", ".xml")):                                                    # Se non esiste un file XML con lo stesso nome dell'immagine
                os.remove(filename)                                                                                     # Elimino l'immagine

if __name__ == '__main__':
    moveMouse()
    print("hello")
