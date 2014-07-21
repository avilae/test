package com.abc.ws.integrations.vendor.service.impl;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;

import org.apache.log4j.Logger;
import org.hrxml25.BackgroundReportsDocument;
import org.hrxml25.BackgroundReportsType;
import org.hrxml25.ErrorReportType;
import org.hrxml25.FlexibleDetailType;
import org.hrxml25.ScreeningPersonalDataType;
import org.hrxml25.ScreeningStatusType;
import org.hrxml25.BackgroundReportsType.BackgroundReportPackage;
import org.hrxml25.EntityIdType.IdValue;
import org.hrxml25.impl.ErrorSeverityTypeImpl;
import org.springframework.ws.server.endpoint.AbstractXomPayloadEndpoint;

import com.abc.service.integrations.vendor.fw.VendorResultProcessService;
import com.abc.service.integrations.vendor.fw.constants.RequestStatus;
import com.abc.service.integrations.vendor.fw.constants.ResultRecordResponseType;
import com.abc.service.integrations.vendor.fw.escreen.EscreenRequest;
import com.abc.service.integrations.vendor.fw.escreen.EscreenRequestData;
import com.abc.service.integrations.vendor.fw.escreen.EscreenResponse;
import com.abc.ws.integrations.vendor.omega.RequestSplitter;
import com.abc.ws.integrations.vendor.utils.FileCapture;
import com.abc.ws.integrations.vendor.utils.Transformer;

public class EscreenBackgroundReportEndpoint extends AbstractXomPayloadEndpoint {

	private static final Logger log = Logger.getLogger(EscreenBackgroundReportEndpoint.class);
	private String logFileStore;
	private String resultsFileStore;

	private VendorResultProcessService vendorResultProcessService;
	private Transformer transformer;
	private static String vendorCompCode = "ESCREEN";
	private static String contextName = "ESCREEN";
	

	public Transformer getTransformer() {
		return transformer;
	}
	public void setTransformer(Transformer transformer) {
		this.transformer = transformer;
	}
	public VendorResultProcessService getVendorResultProcessService() {
		return vendorResultProcessService;
	}
	public void setVendorResultProcessService(
			VendorResultProcessService vendorResultProcessService) {
		this.vendorResultProcessService = vendorResultProcessService;
	}
	public String getLogFileStore() {
		return logFileStore;
	}
	public void setLogFileStore(String logFileStore) {
		this.logFileStore = logFileStore;
	}
	public String getResultsFileStore() {
		return resultsFileStore;
	}
	public void setResultsFileStore(String resultsFileStore) {
		this.resultsFileStore = resultsFileStore;
	}
	
	@Override
	protected Element invokeInternal(Element element) throws Exception {
		log.info("###################### EscreenBackgroundReportEndpoint.invokeInternal");
		String namespaceURI = element.getNamespaceURI();
		if ( namespaceURI == null ){
			throw new Exception( "Namespace missing on Payload" );
		}
		if ( transformer == null ){
			throw new Exception( "Transformer not configured." );
		}
		if ( vendorResultProcessService == null ){
			throw new Exception( "VendorResultProcessService not configured." );
		}
		
		BackgroundReportsDocument responseDocument = BackgroundReportsDocument.Factory.newInstance();
		BackgroundReportsType responseType = responseDocument.addNewBackgroundReports();
		FileCapture fileCapture = new FileCapture(logFileStore);
		FileCapture resultCapture = new FileCapture(resultsFileStore);
		Element respElement = null;
		
		nu.xom.Document reqDoc = new nu.xom.Document( new nu.xom.Element( element ) );
		fileCapture.writeReqLogFile(reqDoc.toXML());
		log.info( "req doc:\n" + reqDoc.toXML() );
		List<Document> resultDocs =  new ArrayList<Document>();
		resultDocs.add(reqDoc);
				 
		String transmissionId = null;		
			int counter = 0;
			//loop through all documents, currently only supports 1 
			for (Document document : resultDocs) {
				log.info( "document:\n" + document.toXML() );
				//write out request file
				String reqFileLocation = resultCapture.writeResultFile(document.toXML(), "results", "escreen", ""+counter);
				BackgroundReportPackage pkg = responseType.addNewBackgroundReportPackage();
				try {
					BackgroundReportsDocument requestDocument = null;
					transformer.setDoLogFile( false );
					requestDocument = transformer.transformRequestAsBackgroundReportsDocument(document.getRootElement(), fileCapture);
					transmissionId = getTransmissionId(requestDocument);
					
					//write out transformed document
					resultCapture.writeResultFile(requestDocument.xmlText(), "results", "escreen", ""+counter+"_"+transmissionId+"_XSLT");
					if( requestDocument!=null ){
						if( requestDocument.getBackgroundReports()!=null && requestDocument.getBackgroundReports().getBackgroundReportPackageArray()!=null &&
								requestDocument.getBackgroundReports().getBackgroundReportPackageArray().length > 0){
							
							EscreenRequest request = getEscreenRequest( requestDocument.getBackgroundReports().getBackgroundReportPackageArray(0) );
							request.setTransmissionId(transmissionId);
							
							((EscreenRequestData)request.getRequestData()).setResultFileLocation(reqFileLocation);
							
							log.info(request);

							EscreenResponse response = null;
							try {
								response = new EscreenResponse();
								vendorResultProcessService.process(request, response);

								//generate response
								if( request.getErrorContainer().haveErrors() && 
										!request.getRequestStatus().equals(RequestStatus.DUPLICATE)){
									response.setResponseType(ResultRecordResponseType.FAIL);
									//hrxml response
									generateErrorAcknowledgement(pkg, request, request.getErrorContainer().toString());
									//Escreen response
									respElement = generateErrorAcknowledgement(transmissionId);
								} else {
									response.setResponseType(ResultRecordResponseType.SUCCESS);
									//hrxml response
									generateSuccessAcknowledgement(pkg, request);
									//Escreen response
									respElement = generateSuccessAcknowledgement(transmissionId);
								}
								
							} catch (Exception e) {
								log.error("error.2", e);
								//generate error response
								generateErrorAcknowledgement(pkg, request, e);
								respElement = generateErrorAcknowledgement(transmissionId);
							}
							
						} else {
							generateErrorAcknowledgement(pkg, new Exception("Could not get BackgroundReportPackage."));
							respElement = generateErrorAcknowledgement(transmissionId);
						}
					}
				} catch (Exception e) {
					log.error("", e);
					generateErrorAcknowledgement(pkg, e);
					respElement = generateErrorAcknowledgement(transmissionId);
				}
				counter++;
			}
		
		
		//IdValue providerReferenceId = responseDocument.getBackgroundReports().addNewProviderReferenceId().addNewIdValue();
		//providerReferenceId.setStringValue(transmissionId);
		//providerReferenceId.setName("providerReferenceId");
		
		//respElement = 	getResponseElement(responseDocument, element);
			
		fileCapture.writeRespLogFile(respElement.toXML());
		return respElement;
	}
	
	private Element getResponseElement(BackgroundReportsDocument responseDocument, Element element) throws Exception {
							
		Element respElement = null;
		Builder builder = new Builder();
		log.info( responseDocument.xmlText() );
		
		nu.xom.Document respDoc = builder.build( responseDocument.xmlText(), element.getNamespaceURI() );
		respElement = respDoc.getRootElement();
		return respElement;
	}
	
	/*
	<ns:BackgroundReportPackage type="report">
		<ns:ProviderReferenceId>
			<ns:IdValue name="ClientId">-1</ns:IdValue>
			<ns:IdValue name="ClientName">Acumium Testing Client</ns:IdValue>
			<ns:IdValue name="SpecimenId">12354680</ns:IdValue>
			<ns:ScreeningsSummary>
				<ns:PersonalData>
					<ns:PersonName>
						<ns:FormattedName>Pierce, Franklin</ns:FormattedName>
					</ns:PersonName>
					<ns:DemographicDetail>
						<ns:GovernmentId>1853-57</ns:GovernmentId>
					</ns:DemographicDetail>
				</ns:PersonalData>
			</ns:ScreeningsSummary>
		</ns:ProviderReferenceId>
		<ns:ScreeningStatus>
			<ns:ResultStatus>Negative</ns:ResultStatus>
		</ns:ScreeningStatus>
	</ns:BackgroundReportPackage>
	 * */
	
	private EscreenRequest getEscreenRequest(BackgroundReportPackage backgroundReportPackage) throws Exception {
		EscreenRequest request = new EscreenRequest();
		request.setRequestData(new EscreenRequestData());
		request.setReceivedTime(new Date());
		request.setContextName( contextName );
		
		String compCode = "";
		String donorName = "";
		String donorId = "";
		String refCode = "";
		String refDesc = "";
		String clientId = "";
		String clientName = "";
		String resultValue = "";
		String dilute = "";
		String regulation = "";
		
		if( backgroundReportPackage!=null ){
			if( backgroundReportPackage.getProviderReferenceId()!=null && 
					backgroundReportPackage.getProviderReferenceId().getIdValueArray()!=null &&
					backgroundReportPackage.getProviderReferenceId().getIdValueArray().length > 0){
				for (int i = 0; i < backgroundReportPackage.getProviderReferenceId().getIdValueArray().length; i++) {
					String attr = (backgroundReportPackage.getProviderReferenceId().getIdValueArray(i).getName()!=null) ? 
							backgroundReportPackage.getProviderReferenceId().getIdValueArray(i).getName() : "";
					if( attr.equals("EScreenId") || attr.equals("PhysicalID")){
						refCode = backgroundReportPackage.getProviderReferenceId().getIdValueArray(i).getStringValue();
					} else if( attr.equals("ClientId")){
						clientId = backgroundReportPackage.getProviderReferenceId().getIdValueArray(i).getStringValue();
					} else if( attr.equals("ClientName")){
						clientName = backgroundReportPackage.getProviderReferenceId().getIdValueArray(i).getStringValue();
					} else if( attr.equals("ABCompCode")){
						compCode = backgroundReportPackage.getProviderReferenceId().getIdValueArray(i).getStringValue();
					}										 
				}
			}
			if( backgroundReportPackage.getScreeningsSummary()!=null &&
					backgroundReportPackage.getScreeningsSummary().getPersonalDataArray()!=null && 
					backgroundReportPackage.getScreeningsSummary().getPersonalDataArray().length > 0 ){
				ScreeningPersonalDataType personalData = backgroundReportPackage.getScreeningsSummary().getPersonalDataArray(0);
				if(  personalData.getPersonNameArray()!=null && 
						personalData.getPersonNameArray().length > 0){
					donorName = personalData.getPersonNameArray(0).getFormattedName();
				}
				if( personalData.getDemographicDetail()!=null &&
						personalData.getDemographicDetail().getGovernmentIdArray()!=null &&
						personalData.getDemographicDetail().getGovernmentIdArray().length > 0){
					donorId = formatDonorId(personalData.getDemographicDetail().getGovernmentIdArray(0).getStringValue());
				}
			}
			if( backgroundReportPackage.getScreeningStatus()!=null && backgroundReportPackage.getScreeningStatus().getResultStatus()!=null ){
				resultValue = backgroundReportPackage.getScreeningStatus().getResultStatus();
			}
			if( backgroundReportPackage.getAdditionalItemsArray()!=null && backgroundReportPackage.getAdditionalItemsArray().length>0 ){
				for (int i = 0; i < backgroundReportPackage.getAdditionalItemsArray().length; i++) {
					FlexibleDetailType flexibleDetailType = backgroundReportPackage.getAdditionalItemsArray(i);
					String qualifier = flexibleDetailType.getQualifier()!=null ? flexibleDetailType.getQualifier() : "";
					String[] textArray = flexibleDetailType.getTextArray();
					EscreenRequestData escreenRequestData = (EscreenRequestData)request.getRequestData();
					if( qualifier.equals("GeneralRecordText") ){
						if( textArray!=null && textArray.length>0 ){
							escreenRequestData.setGeneralRecordReport(flexibleDetailType.getTextArray(0));
						}
					} else if( qualifier.equals("CollectionDateTime") ){
						if( textArray!=null && textArray.length>0 ){
							escreenRequestData.setCollectionDate( formatDate(flexibleDetailType.getTextArray(0)) );
						}						
					}else if( qualifier.equals("ReceivedDateTime") ){
						if( textArray!=null && textArray.length>0 ){
							escreenRequestData.setReceivedDate( formatDate(flexibleDetailType.getTextArray(0)) );
						}						
					}else if( qualifier.equals("ReportDateTime") ){
						if( textArray!=null && textArray.length>0 ){
							escreenRequestData.setReportDate( formatDate(flexibleDetailType.getTextArray(0)) );
						}						
					}else if( qualifier.equals("VerificationDateTime") ){
						if( textArray!=null && textArray.length>0 ){
							escreenRequestData.setVerificationDate( formatDate(flexibleDetailType.getTextArray(0)) );
						}						
					}else if( qualifier.equals("Dilute") ){
						if( textArray!=null && textArray.length>0 ){
							dilute  = flexibleDetailType.getTextArray(0); 
							request.setDilute(dilute);
						}
					}else if( qualifier.equals("Regulation") ){
						if( textArray!=null && textArray.length>0 ){
							regulation   = flexibleDetailType.getTextArray(0); 
							request.setRegulation( regulation );
						}
					}
				}
			}
			refDesc += "Donor Name: " + donorName + "; Client Id:" + clientId + "; Client Name:" + clientName + "; Result:" + resultValue + ";"+" Diluted?: "+request.isDiluted();			
		}
		
		request.setEScreenId(refCode);
		request.setCompCode(compCode);
		request.setDonorId(donorId);
		request.setDonorName(donorName);
		request.setRefCode(refCode);
		request.setRefDesc(refDesc);
		request.setVendorCode( vendorCompCode );
		request.setDrugTestResult( resultValue );	
		request.setClientCode(clientId);		
	
		return request;
	}
	
	private Date formatDate(String dateValue) throws Exception {		
		SimpleDateFormat parser = new SimpleDateFormat("MM/dd/yyyy h:mm:ss a");
		SimpleDateFormat formatter = new SimpleDateFormat ("MM/dd/yyyy k:mm:ss");
		Date collectionDate = null;			
		Date tempDate = null;
		if( dateValue!=null && dateValue.trim().length() > 0 ){
			try {
				//int lastIdx = dateValue.lastIndexOf('.');
				//dateValue = dateValue.substring(0,lastIdx);
				dateValue = dateValue.replaceAll("T", " ");
				tempDate = parser.parse(dateValue);			
				dateValue = formatter.format(tempDate);
				collectionDate = formatter.parse(dateValue);
				
			} catch (Exception e) {
				log.error("", e);
			}
		}
		return collectionDate;
	}
	
	private String formatDonorId(String donorId) throws Exception {
		if( donorId!=null ){
			donorId = donorId.trim().replaceAll(" ", "");
			donorId = donorId.replaceAll("-", "");
			return donorId;
		} else {
			return donorId;
		}
	}
	
	@SuppressWarnings("unused")
	private String getRefCode(BackgroundReportsDocument responseDocument) throws Exception {
		String refCode = null;
		if( responseDocument!=null && responseDocument.getBackgroundReports()!=null && 
				responseDocument.getBackgroundReports().getBackgroundReportPackageArray()!=null &&
				responseDocument.getBackgroundReports().getBackgroundReportPackageArray().length > 0){
			BackgroundReportPackage backgroundReportPackage = responseDocument.getBackgroundReports().getBackgroundReportPackageArray(0);
			if( backgroundReportPackage!=null ){
				if( backgroundReportPackage.getProviderReferenceId()!=null && 
						backgroundReportPackage.getProviderReferenceId().getIdValueArray()!=null &&
						backgroundReportPackage.getProviderReferenceId().getIdValueArray().length > 0){
					for (int i = 0; i < backgroundReportPackage.getProviderReferenceId().getIdValueArray().length; i++) {
						String attr = (backgroundReportPackage.getProviderReferenceId().getIdValueArray(i).getName()!=null) ? 
								backgroundReportPackage.getProviderReferenceId().getIdValueArray(i).getName() : "";
						if( attr.equals("eScreenID")){
							refCode = backgroundReportPackage.getProviderReferenceId().getIdValueArray(i).getStringValue();
							break;
						}
					}
				}
			}
		}
		return refCode;
	}
		 
	private String getTransmissionId(BackgroundReportsDocument responseDocument) throws Exception {
		String batchId = null;
		if( responseDocument!=null && responseDocument.getBackgroundReports()!=null &&
				responseDocument.getBackgroundReports().getProviderReferenceId()!=null &&
				responseDocument.getBackgroundReports().getProviderReferenceId().getIdValueArray()!=null &&
				responseDocument.getBackgroundReports().getProviderReferenceId().getIdValueArray().length > 0){
			for (int i = 0; i < responseDocument.getBackgroundReports().getProviderReferenceId().getIdValueArray().length; i++) {
				String attr = (responseDocument.getBackgroundReports().getProviderReferenceId().getIdValueArray(i).getName()!=null) ? 
						responseDocument.getBackgroundReports().getProviderReferenceId().getIdValueArray(i).getName() : "";
				if( attr.equals("TransmissionID")){
					batchId = responseDocument.getBackgroundReports().getProviderReferenceId().getIdValueArray(i).getStringValue();
					break;
				}
			}
		}
		return batchId;
	}
	private void generateErrorAcknowledgement( BackgroundReportPackage reportPkg, Exception exception ){
    	generateErrorAcknowledgement(reportPkg, null, exception);
		
    }
	private Element  generateAcknowledgement( String transmissionId, String status ){    			
		Element respElement = new Element("eScreenData");    
		Attribute transIDAtt = new Attribute("TransmissionID",transmissionId);
		Attribute statusAtt = new Attribute("status",status);
		respElement.addAttribute(transIDAtt);
		respElement.addAttribute(statusAtt);
		return respElement;
    }
	private Element generateErrorAcknowledgement( String transmissionId){
		return generateAcknowledgement(transmissionId, "F");
	}
	private Element generateSuccessAcknowledgement( String transmissionId){
		return generateAcknowledgement(transmissionId, "S");
	}
	private void generateErrorAcknowledgement( BackgroundReportPackage reportPkg, EscreenRequest request, Exception exception ){
    	if( request!=null ){
    		reportPkg.addNewProviderReferenceId().addNewIdValue().setStringValue( request.getRefCode() );
    	}
        ErrorReportType errorReport = reportPkg.addNewErrorReport();
        errorReport.setErrorSeverity( ErrorSeverityTypeImpl.FATAL.toString() );
        errorReport.setErrorCode( "4000" );
        errorReport.setErrorDescription( "Internal Server Error during background check request. " + exception.getMessage() );
    }
    
	private void generateErrorAcknowledgement( BackgroundReportPackage reportPkg, EscreenRequest request, String msg ){
    	if( request!=null ){
    		reportPkg.addNewProviderReferenceId().addNewIdValue().setStringValue( request.getRefCode() );
    	}
        ErrorReportType errorReport = reportPkg.addNewErrorReport();
        errorReport.setErrorSeverity( ErrorSeverityTypeImpl.FATAL.toString() );
        errorReport.setErrorCode( "4000" );
        errorReport.setErrorDescription( "Internal Server Error during background check request. " + msg );
    }

	private void generateSuccessAcknowledgement( BackgroundReportPackage reportPkg, EscreenRequest request ){
    	reportPkg.addNewProviderReferenceId().addNewIdValue().setStringValue( request.getRefCode() );
    	ScreeningStatusType screeningStatus  = reportPkg.addNewScreeningStatus();
    	screeningStatus.setOrderStatus("Completed");
    }    
    
}
