package com.abc.ws.integrations.vendor.service.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import nu.xom.Builder;
import nu.xom.Element;
import nu.xom.xslt.XSLTransform;

import org.apache.log4j.Logger;
import org.hrxml25.BackgroundReportsDocument;
import org.hrxml25.BackgroundReportsType;
import org.hrxml25.BackgroundReportsType.BackgroundReportPackage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.server.endpoint.AbstractXomPayloadEndpoint;

import com.abc.service.integrations.vendor.cs.service.BackgroundReportService;
import com.abc.ws.integrations.vendor.cs.BackgroundReportProcessor;
import com.accuratebackground.br.x10.BackgroundReportRequestDocument;

public class BackgroundReportEndpoint extends AbstractXomPayloadEndpoint {

	private static final Logger log = Logger.getLogger(BackgroundReportEndpoint.class);
	private SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd-kk-mm-ss-SSS" );
	private SimpleDateFormat sdf2 = new SimpleDateFormat( "yyyy-MM-dd" );
	private String requestXSLTFileName;
	private String responseXSLTFileName;
	private String requestLogFileStore;
	private String responseLogFileStore;
	private BackgroundReportService backgroundReportService;

	public BackgroundReportService getBackgroundReportService() {
		return backgroundReportService;
	}
	public void setBackgroundReportService(
			BackgroundReportService backgroundReportService) {
		this.backgroundReportService = backgroundReportService;
	}

	public String getRequestXSLTFileName() {
		return requestXSLTFileName;
	}
	public void setRequestXSLTFileName(String requestXSLTFileName) {
		this.requestXSLTFileName = requestXSLTFileName;
	}

	public String getResponseXSLTFileName() {
		return responseXSLTFileName;
	}
	public void setResponseXSLTFileName(String responseXSLTFileName) {
		this.responseXSLTFileName = responseXSLTFileName;
	}

	public String getRequestLogFileStore() {
		return requestLogFileStore;
	}
	public void setRequestLogFileStore(String requestLogFileStore) {
		this.requestLogFileStore = requestLogFileStore;
	}

	public String getResponseLogFileStore() {
		return responseLogFileStore;
	}
	public void setResponseLogFileStore(String responseLogFileStore) {
		this.responseLogFileStore = responseLogFileStore;
	}

	@Override
	protected Element invokeInternal(Element element) throws Exception {
		String namespaceURI = element.getNamespaceURI();
		if ( namespaceURI == null ){
			throw new Exception( "Namespace missing on Payload" );
		}
		Builder builder = new Builder();
		String logTimestamp = sdf.format(Calendar.getInstance().getTime() );
		String refId = "";
		BackgroundReportsDocument backgroundReportsDocument = null;
		nu.xom.Document reqDoc = new nu.xom.Document( new nu.xom.Element( element ) );
		log.debug( "req doc:\n" + reqDoc.toXML() );
		BackgroundReportRequestDocument brrd = BackgroundReportRequestDocument.Factory.parse(reqDoc.toXML());
		writeReqLogFile(logTimestamp, false, "", brrd.getBackgroundReportRequest().getBackgroundReportResult());
		BackgroundReportRequestDocument backgroundReportRequestDocument = BackgroundReportRequestDocument.Factory.parse( reqDoc.toXML() );
		nu.xom.Document brreqDoc = builder.build( backgroundReportRequestDocument.getBackgroundReportRequest().getBackgroundReportResult(), element.getNamespaceURI() );
		
		if( requestXSLTFileName!=null ){
			nu.xom.Document transformedReqDoc = transform(requestXSLTFileName, brreqDoc);
			backgroundReportsDocument = BackgroundReportsDocument.Factory.parse( transformedReqDoc.toXML() );
			refId = getRefId(backgroundReportsDocument);
			writeReqLogFile(logTimestamp, true, refId, transformedReqDoc.toXML());
		} else {
			backgroundReportsDocument = BackgroundReportsDocument.Factory.parse( brreqDoc.toXML() );
		}

		BackgroundReportProcessor processor = new BackgroundReportProcessor();
		BackgroundReportsDocument response = processor.process( backgroundReportService, backgroundReportsDocument );

		Element respElement = null;
		nu.xom.Document respDoc = builder.build( response.xmlText(), element.getNamespaceURI() );
		log.debug( "resp doc:\n" + respDoc.toXML() );
		writeRespLogFile(logTimestamp, false, refId, respDoc.toXML());
		if( responseXSLTFileName!=null ){
			nu.xom.Document transformedRespDoc = transform(responseXSLTFileName, respDoc);
			respElement = transformedRespDoc.getRootElement();
			writeRespLogFile(logTimestamp, true, refId, transformedRespDoc.toXML());
		} else {
			respElement = respDoc.getRootElement();
		}
		return respElement;
	}
	
	private String getRefId(BackgroundReportsDocument backgroundReportsDocument){
		StringBuffer id = new StringBuffer();
		try {
			BackgroundReportsType backgroundReportsType = backgroundReportsDocument.getBackgroundReports();
			for (int i = 0; i < backgroundReportsType.getBackgroundReportPackageArray().length; i++) {
				try {
					BackgroundReportPackage backgroundReportPackage = backgroundReportsType.getBackgroundReportPackageArray(i);
					String t = backgroundReportPackage.getProviderReferenceId().getIdValueArray(0).getStringValue().trim();
					id.append( "_" + t );
				} catch (Exception e) {
					log.error("Error.2", e);
				}
			}
		} catch (Exception e) {
			log.error("Error.1", e);
		}
		return id.toString();
	}

	@SuppressWarnings("static-access")
	public nu.xom.Document transform(String xsltFilename, nu.xom.Document doc) throws Exception {
		nu.xom.Document transformedDoc = null;
		if( xsltFilename!=null ){
			Builder builder = new Builder();
			ClassPathResource c = new ClassPathResource( xsltFilename );
			XSLTransform transformResp = new XSLTransform( builder.build( c.getFile() ) );
			transformedDoc = transformResp.toDocument( transformResp.transform( doc ) );
			//if ( log.isDebugEnabled() ){
			log.debug( "transformed resp doc:" + transformedDoc.toXML() );
			//}
		} else {
			throw new Exception("xsltFilename is null");
		}
		return transformedDoc;
	}
	
	public String generateFilename( String filepath, String filePrefix, String filePostfix, String outDate, String fileext ) throws Exception {
		//String outDate = sdf.format(Calendar.getInstance().getTime());
		String outFilename = filepath+filePrefix+outDate+filePostfix+"."+fileext;
		return outFilename;
	}

	private void writeRespLogFile( String outDate, boolean isTransformed, String refId, String info ) throws Exception {
		writeLogFile( responseLogFileStore, outDate, refId, false, isTransformed, info );
	}
	private void writeReqLogFile( String outDate, boolean isTransformed, String refId, String info ) throws Exception {
		writeLogFile( requestLogFileStore, outDate, refId, true, isTransformed, info );
	}
	private void writeLogFile( String filepath, String outDate, String refId, boolean isRequest, boolean isTransformed, String info ) throws Exception {
		String filePrefix = (isRequest) 		? "req_" 	: "resp_" ;
		String filePostfix = (isTransformed) 	? refId+"-XSLT" : refId+"" ;
		filepath += sdf2.format(Calendar.getInstance().getTime()) + "\\";
		String logFilename = generateFilename( filepath, filePrefix, filePostfix, outDate, "xml" );
		writeLogFile(filepath, logFilename, info);
	}
	public void writeLogFile( String filepath, String outFilename, String info ) {
		if ( filepath != null ){
			try {
				FileWriter fw = null;
				PrintWriter pw = null;
				String newFile = null;
				Exception exception = null;
				try {            
					File outFile = new File( filepath );
					outFile.mkdirs();
					fw = new FileWriter( outFilename );
					pw = new PrintWriter( fw );
					pw.print( new String( info.getBytes( "UTF-8") ) );
					//pw.print( info.toCharArray() );
				} catch ( Exception e ) {
					exception = e;
				} finally {
					if ( fw != null ){
						fw.flush();
						fw.close();
					}
					if ( pw != null ){
						pw.flush();
						pw.close();
					}            
				}
				if ( exception != null ){
					throw exception;     
				}
			} catch (Exception e) {
				log.error("Error on writeLogFile ", e);
			}
		} else {
			log.warn("filepath is null.");
		}
	}

}
