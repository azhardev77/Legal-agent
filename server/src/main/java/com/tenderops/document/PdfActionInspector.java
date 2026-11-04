package com.tenderops.document;

import com.tenderops.common.PdfRejectedException;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;

/**
 * Rejects PDFs with active content: OpenAction, document JavaScript, page/annotation
 * actions, and JavaScript/Launch actions anywhere (TC-ING-01, §9).
 */
public final class PdfActionInspector {

  private PdfActionInspector() {}

  public static void rejectIfDangerous(PDDocument doc) {
    COSDictionary catalog = doc.getDocumentCatalog().getCOSObject();
    if (catalog.getItem(COSName.OPEN_ACTION) != null) {
      throw new PdfRejectedException("ACTIVE_CONTENT",
          "PDF has an OpenAction and is rejected");
    }
    rejectActionsIn(catalog.getCOSDictionary(COSName.AA), "catalog");
    COSDictionary names = catalog.getCOSDictionary(COSName.NAMES);
    if (names != null && names.getCOSDictionary(COSName.getPDFName("JavaScript")) != null) {
      throw new PdfRejectedException("ACTIVE_CONTENT",
          "PDF contains document-level JavaScript and is rejected");
    }
    try {
      for (PDPage page : doc.getPages()) {
        rejectActionsIn(page.getCOSObject().getCOSDictionary(COSName.AA), "page");
        for (PDAnnotation annotation : page.getAnnotations()) {
          COSDictionary annot = annotation.getCOSObject();
          rejectAction(annot.getCOSDictionary(COSName.A), "annotation");
          rejectActionsIn(annot.getCOSDictionary(COSName.AA), "annotation");
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot inspect PDF annotations", e);
    }
  }

  private static void rejectActionsIn(COSDictionary aa, String where) {
    if (aa == null) {
      return;
    }
    for (COSBase entry : aa.getValues()) {
      if (entry instanceof COSDictionary dict) {
        rejectAction(dict, where + " additional action");
      }
    }
    if (aa.size() > 0) {
      throw new PdfRejectedException("ACTIVE_CONTENT",
          "PDF " + where + " has additional actions and is rejected");
    }
  }

  private static void rejectAction(COSDictionary action, String where) {
    if (action == null) {
      return;
    }
    String type = action.getNameAsString(COSName.S);
    if ("JavaScript".equals(type) || "Launch".equals(type)) {
      throw new PdfRejectedException("ACTIVE_CONTENT",
          "PDF " + where + " has a " + type + " action and is rejected");
    }
  }
}
