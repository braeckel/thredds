package ucar.nc2.ogc2.waterml;

import net.opengis.waterml.x20.DocumentMetadataType;
import java.util.GregorianCalendar;

/**
 * Created by cwardgar on 2014/03/13.
 */
public abstract class NC_DocumentMetadataType {
    // wml2:Collection/wml2:metadata/wml:DocumentMetdata
    public static DocumentMetadataType initDocumentMetadata(DocumentMetadataType documentMetadata) {
        // gml:id
        documentMetadata.setId(generateId());

        // wml2:generationDate
        GregorianCalendar gregorianCalendar = new GregorianCalendar();  // Initialized to "now".
        documentMetadata.setGenerationDate(gregorianCalendar);

        return documentMetadata;
    }

    private static int numIds = 0;

    private static String generateId() {
        return DocumentMetadataType.class.getSimpleName() + "." + ++numIds;
    }

    private NC_DocumentMetadataType() { }
}
