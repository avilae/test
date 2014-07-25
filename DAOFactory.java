package com.abc.bo.dao;

import org.apache.log4j.Logger;

import com.abc.bo.DaoInterface;
import com.abc.bo.impl.hbm.HibernateDAOFactory;

/**
 * DAOFactory based on abstract factory design pattern ->  
 * @link http://java.sun.com/blueprints/corej2eepatterns/Patterns/DataAccessObject.html
 */

public abstract class DAOFactory {

	  static final Logger log = Logger.getLogger( DAOFactory.class ); 
		  
      // Used for abstraction persistant layer ORM
	  public static final int HIBERNATE  = 0;  
	  
	  // RDBMS supported
	  public static final int ORACLE     = 1;
	  public static final int SYBASE     = 2;
	  public static final int CLOUDSCAPE = 3;
	  public static final int SQLSERVER  = 4;	  
	  public static final int MYSQL      = 5;	
	  public static final int HYPERSONIC = 6;
	  
	  public static synchronized DAOFactory createDAOFactory() throws Exception {
        return createDAOFactory( HIBERNATE );		    
	  }
	  
	  public static synchronized DAOFactory createDAOFactory( int selection ) throws Exception {
	  
	    switch (selection) {
	      
	      case HIBERNATE  : return new HibernateDAOFactory();
	      // case HYPERSONIC : return new HypersonicDAOFactory();       
	      // add more as needed to implement
	      default         : return null;
	    }
	    
	  }
	  
    ////////////////  abstract business DAOs Factories  /////////////////////
	public abstract DaoInterface    createDao()            throws Exception;
	public abstract DaoInterface    createDao( Object sf ) throws Exception;   
	// TODO add more
		
}
