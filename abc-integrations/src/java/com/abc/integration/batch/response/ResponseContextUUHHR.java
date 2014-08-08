/*
NAME:             ResponseContextUUHHR
DESCRIPTION:      Response file generator for batch process 
CREATED:          08/04/2014
AUTHOR:           Ernesto Avila (eavila)
ACCURATE BACKGROUND, INC. ("COMPANY") CONFIDENTIAL
(C) Copyright 2013 Accurate Background, Inc., All Rights Reserved.

NOTICE:  All information contained herein is, and remains the property of COMPANY. The 
intellectual and technical concepts contained herein are proprietary to COMPANY and may be 
covered by U.S. and Foreign Patents, patents in process, and are protected by trade secret 
or copyright law. Dissemination of this information or reproduction of this material is 
strictly forbidden unless prior written permission is obtained from COMPANY.  Access to the 
source code contained herein is hereby forbidden to anyone except current COMPANY employees, 
managers or contractors who have executed Confidentiality and Non-disclosure agreements 
explicitly covering such access.

The copyright notice above does not evidence any actual or intended publication or 
disclosure of this source code, which includes information that is confidential and/or 
proprietary, and is a trade secret, of COMPANY.  ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC PERFORMANCE, OR PUBLIC DISPLAY OF OR THROUGH USE OF THIS SOURCE CODE WITHOUT THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED, AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.  THE RECEIPT OR POSSESSION OF  THIS SOURCE CODE AND/OR RELATED INFORMATION DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS, OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT MAY DESCRIBE,IN WHOLE OR IN PART.
*/
package com.abc.integration.batch.response;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import com.abc.bo.hbm.Abcuser;
import com.abc.bo.hbm.Search;
import com.abc.commons.validation.AbcError;
import com.abc.integration.batch.BatchProcessor;
import com.abc.service.odrproc.bo.Order;
import com.abc.service.reports.util.EncryptURL;

public class ResponseContextUUHHR extends ResponseContextProcessor {

	private static Log log = LogFactory.getLog(ResponseContextUUHHR.class);
	
	
	public static final ResourceBundle batchResources = ResourceBundle.getBundle("batchprocess"); 
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(batchResources.getString("dateformat"));
	
	/**
	 * This Method is used for generate the fail response  
	 * 
	 * @param BatchProcessor
	 * @return void
	 */
	public void getFailResponse(BatchProcessor batchProcessor) {
		try{
		List<Node> list = new ArrayList<Node>();
		if ( batchProcessor!=null )
		if( batchProcessor.getContext()!=null ){
			list.add( new Node(batchResources.getString("AB-CLIENT-REF"), batchProcessor.getContext().getContextName()) );
		} else {
			list.add( new Node(batchResources.getString("AB-CLIENT-REF"), "") );
		}
		list.add( new Node(batchResources.getString("AB-ACK-DATE"), DATE_FORMAT.format(new Date())) ); 
		list.add( new Node(batchResources.getString("AB-ACK-STATUS"), batchResources.getString("UnableToProcess")) ); 
		list.add( new Node(batchResources.getString("AB-ERROR-MSG"), batchProcessor.getException().getMessage()) ); 
		list.add( new Node(batchResources.getString("AB-PKG-STATUS"), "") ); 
		list.add( new Node(batchResources.getString("AB-ADJ-STATUS-DESC"), "") );	
		list.add( new Node(batchResources.getString("AB-REPORT-URL"), ""));
		list.add( new Node(batchResources.getString("AB-ADJ-DATE"), "") ); 
		this.getElements().put("1", list);
		}catch(Exception e){
			nullResponse(e);
		}
	}

	/**
	 * This Method is used for generate the Success response  
	 * 
	 * @param BatchProcessor
	 * @return void
	 */
	public void getSuccessResponse(BatchProcessor batchProcessor) {
		try{
			ProcessorConfig processorConfig = getProcessorConfig();
			int counter=0;
			for (Order order : batchProcessor.getBatchOrder().getOrders()) {
				counter++;
				List<Node> list = new ArrayList<Node>();
				Search header = order.getSearchPackageRequest().getSearchPackage().getHeader();
				Search mainSearch = header;
				if( mainSearch==null ){
					if( order.getSearchPackageRequest().getSearchPackage().getFirstComponent()!=null ){
						mainSearch = order.getSearchPackageRequest().getSearchPackage().getFirstComponent().getSearchComponent();
					}
				}
				if(mainSearch!=null){
					
					list.add( new Node(batchResources.getString("search_id"), String.valueOf(mainSearch.getSearchId())));
					list.add( new Node(batchResources.getString("PackageReqId"),  header.getPackageReqId()));
					list.add( new Node(batchResources.getString("AB-CLIENT-REF"), order.getSearchPackageRequest().getClientRefNumber()));
				}else {
					list.add( new Node(batchResources.getString("search_id"), ""));
					list.add( new Node(batchResources.getString("PackageReqId"), ""));
					list.add( new Node(batchResources.getString("AB-CLIENT-REF"), order.getSearchPackageRequest().getClientRefNumber()));
				}
				list.add( new Node(batchResources.getString("AB-ACK-DATE"), DATE_FORMAT.format(new Date())));
				if(order.haveErrors()){
					StringBuffer strb = new StringBuffer();
					for (AbcError error : order.getErrorContainer().getErrors()) {
						strb.append(error.getErrorTitle().trim() + ". " + error.getAdditionalMessage());
					}
					list.add( new Node(batchResources.getString("AB-ACK-STATUS"), batchResources.getString("UnableToProcess")));
					list.add( new Node(batchResources.getString("AB-ERROR-MSG"), strb.toString()));
				}  else {
					list.add( new Node(batchResources.getString("AB-ACK-STATUS"), batchResources.getString("AcceptedInProgress")));
					list.add( new Node(batchResources.getString("AB-ERROR-MSG"), ""));
				}
				list.add( new Node(batchResources.getString("AB-PKG-STATUS"), ""));
				list.add( new Node(batchResources.getString("AB-ADJ-STATUS-DESC"), ""));
				String url = "";
				try {
					url = formatDetailReportURL(processorConfig, order.getSearchPackageRequest().getSearchPackage().getRequestor(), header.getPackageReqId(), true, false);				
				} catch (Exception e) {
					log.error("", e);
				}
				list.add( new Node(batchResources.getString("AB-REPORT-URL"), url));
				list.add( new Node(batchResources.getString("AB-ADJ-DATE"), ""));
				this.getElements().put(String.valueOf(counter), list);
			}	
		}catch(Exception e){
			nullResponse(e);
		}
			

	}
	
	/**
	 * This Method is used for generate the fail response  
	 * 
	 * @param BatchProcessor
	 * @return void
	 */
	public void nullResponse(Exception e){
		List<Node> list = new ArrayList<Node>();
		list.add( new Node(batchResources.getString("AB-CLIENT-REF"), "") );
		list.add( new Node(batchResources.getString("AB-ACK-DATE"), "") ); 
		list.add( new Node(batchResources.getString("AB-ACK-STATUS"), "") ); 
		list.add( new Node(batchResources.getString("AB-ERROR-MSG"),e.getMessage() ) ); 
		list.add( new Node(batchResources.getString("AB-PKG-STATUS"), "") ); 
		list.add( new Node(batchResources.getString("AB-ADJ-STATUS-DESC"), "") );	
		list.add( new Node(batchResources.getString("AB-REPORT-URL"), ""));
		list.add( new Node(batchResources.getString("AB-ADJ-DATE"), "") ); 
		this.getElements().put("1", list);
	}
	
	public String formatDetailReportURL( ProcessorConfig processorConfig, Abcuser abcuser, String packageReqId, Boolean loginRequired, boolean timedUrl ) throws Exception 
	{
		long timestamp = Calendar.getInstance().getTimeInMillis();
		if( !timedUrl ){
			timestamp = timestamp * -1;
		}

		HashMap<String, String> encryptParms = new HashMap<String, String>();

		//  User Id
		Integer userId = abcuser.getUserId();

		//  URL includes timestamp so that it can be expired
		encryptParms.put( "t", Long.toString( timestamp ) );        
		encryptParms.put( "a", "n" );

		//  Result Access Level
		if ( userId != null )
			encryptParms.put( "l", abcuser.getResultAccessLevel() );

		//  Package Req Id
		if ( packageReqId != null )
			encryptParms.put( "e", packageReqId );

		//  User ID
		if ( userId != null )
			encryptParms.put( "o", userId.toString() );

		//  Login Required parameter
		encryptParms.put( "c", loginRequired == null ? "false" : loginRequired.toString() );

		/* 
        //  This is test data
        encryptParms.put( "t", Long.toString( timestamp ) );        
        encryptParms.put( "a", "n" );
        encryptParms.put( "l", "6" );
        encryptParms.put( "e", "Y1878556" );
        encryptParms.put( "o", "1234" );
		 */
		return EncryptURL.getEncryptedURL( processorConfig.getReportUrl(), encryptParms );
	}

	

}