package com.codegenerator.db;

import java.sql.*;
import java.util.*;
import com.codegenerator.common.ApplicationObject;
import com.codegenerator.common.ApplicationProperties;
import com.codegenerator.common.EzwGen;
import com.codegenerator.common.Functions;
import com.codegenerator.common.ListHashtable;
import com.codegenerator.common.ThreadContext;

public class SqlTable extends ApplicationObject {
	
	protected String table;
	protected String schema;
	protected String entityName;
	protected String entityNameFromTableName;
	
	protected List        sqlColumns;
	protected HashMap     allColumns;
	protected List        primaryKeys;
	protected Boolean     hasTestableColumn  = null;
	protected SqlColumn   testableColumn = null;
	protected boolean     hasVersion = false;
	protected List        typeList;
	protected boolean     generated = false;
	protected ForeignKeys  exportedKeys  = null;
	protected ForeignKeys  importedKeys = null;
	
	public    static final String PK_NAME       = "PK_NAME";
	public    static final String COLUMN_NAME   = "COLUMN_NAME";
	public    static final String PKTABLE_NAME  = "PKTABLE_NAME";
	public    static final String PKCOLUMN_NAME = "PKCOLUMN_NAME";
	public    static final String FKTABLE_NAME  = "FKTABLE_NAME";
	public    static final String FKCOLUMN_NAME = "FKCOLUMN_NAME";
	public    static final String UPDATE_RULE   = "UPDATE_RULE";
	public    static final String DELETE_RULE   = "DELETE_RULE";
	public    static final String KEY_SEQ       = "KEY_SEQ";
	 
/**
 * SqlTable constructor comment.
 * 
 * 2004-11-24 Nathan Anderson [nanderson@melinate.com]
 * - Added logic for case sensitive table & schema names
 *   This is necessary for PostgreSQL and other case
 *   sensitive databases 
 * 
 * 2004-11-28 Richard Che So [c.so@sympatico.ca]
 * Add new parameter entity name which will default to tbl name
 * This new parameter can be passed in from an Erwin CSV report
 */
public SqlTable(String tbl, String sch) {

	 this(tbl, sch, tbl, true,true);
	
	
}
// 2004-11-28 - Add entity name and additional boolean flag. The last boolean 
// flag (default to true) indicates whether or not to get
// database info from reading the database schema
// 2004-12-9 - Richard Rowell [richard@rowell.info]
// Added pojoName parameter which populates into entityName
public SqlTable(String tbl, String sch, boolean isCaseSensitive, String pojoName) {

	 this(tbl, sch, pojoName, isCaseSensitive, true );
	
	
}
/**
 * @param tbl
 * @param sch
 * @param fromDb
 * Constructor for an SQL table called by default with fromDb=true if
 * only the table and schema are used in the constructor
 */
public SqlTable (String tbl, String sch, String entity, boolean isCaseSensitive, boolean fromDb) {
	super();
	// Fix Foreign Key endless loop problem
	String originalTableName = tbl;
	if (!isCaseSensitive) {
		tbl = tbl.trim().toUpperCase();
		sch = sch.trim().toUpperCase();		
	}
	table  = tbl.trim();
	schema = sch.trim();

	entityNameFromTableName = tbl.trim();
	
	if (entity != null)
		entityName = entity.trim();
	else
		entityName = null;
	
	initHolders();
	if (fromDb) 
		getTableInfoFromDatabase();
	
	// put instantiated table in application hashtable instance
	// so any additional tables generated by this instance
	// will be automatically generated (e.g. via foreign keys)
	ApplicationProperties.getSqlTables().put(originalTableName,this);
	List eKeys = getExportedKeys().getAssociatedTables().getOrderedKeys();
	List iKeys = getImportedKeys().getAssociatedTables().getOrderedKeys();
	initForeignKeyTables(eKeys,sch,isCaseSensitive,fromDb);
	initForeignKeyTables(iKeys,sch,isCaseSensitive,fromDb);
}
/**
 * 
 */
private void initHolders() {
	sqlColumns  = new ArrayList();
	primaryKeys = new ArrayList();
	allColumns  = new HashMap();
	typeList = new ArrayList();
}
/**
 * This method was created in VisualAge.
 */
private void getFirstUniqueIndex(DatabaseMetaData dbmd) {
	try {

		   ListHashtable pk = new ListHashtable();
	       ResultSet indexes = null;
               try{
                   indexes = dbmd.getIndexInfo(null,schema,table,false,false);
               }catch(Exception e){
                   return;
               }
		   int numindexes       = 0 ;
		   int indcolcnt        = 0 ;
		   
		   String NON_UNIQUE       = "NON_UNIQUE";
		   String INDEX_QUALIFIER  = "INDEX_QUALIFIER" ;
		   String INDEX_NAME       = "INDEX_NAME" ;
		   String INDCOL_NAME      = "COLUMN_NAME" ;
		   String ASC_OR_DESC      = "ASC_OR_DESC";
		   String INDEX_TYPE       = "TYPE" ;
		   
		   String cindstr       = "";
		   String indline       = "";
		   String index         = "";
		   String previndex     = "";
		   String ascendstr     = "";
		   String indstring     = "";
		   boolean found        = false;
		   while ( indexes.next()) {
				 String indname = indexes.getString(INDEX_NAME);
				 if (indname != null)
				 {
						String indqual    = indexes.getString(INDEX_QUALIFIER);
						String  asc       = indexes.getString(ASC_OR_DESC);
						boolean  nonunique = indexes.getBoolean(NON_UNIQUE);
						String colname    = indexes.getString(INDCOL_NAME);					
						String seq        = "1";
						try{
							seq        = indexes.getString(KEY_SEQ);
						}catch(Exception e){
							break;
						}
						Integer iseq      = new Integer(seq);
						previndex         = index;
						index             = indqual.trim()+"."+indname.trim();
						if (!previndex.equals(index) && !previndex.equals("")) {
						   if (found) break;
						}
						if (!nonunique) found = true;
						if (found) {
							String keystr = colname;
			                SqlColumn x = (SqlColumn) allColumns.get(keystr);
			                x.setKey(true);
			                pk.put(iseq,x);
						}
				 }
		   } // end while
		   

		   indexes.close();
		   primaryKeys = pk.getOrderedValues();
		   
	} catch (Exception e) {
		e.printStackTrace();
		throw (new RuntimeException("Error getting first unique index for " + table +
			   " : " + e.getMessage()));
	}

	
}
/**
 * This method was created in VisualAge.
 */
private void getIndexInfo(DatabaseMetaData dbmd) throws java.sql.SQLException {
	
		   // get primary keys and set up a vector of primary Keys
		   // set SqlColumn's attribute isKey to true.

		   ListHashtable pk = new ListHashtable();
		   
		   ResultSet pkeys = dbmd.getPrimaryKeys(null,schema,table);
		  
		   while ( pkeys.next()) {
			 String pkeystr = pkeys.getString(COLUMN_NAME);
			 String seq     = pkeys.getString(KEY_SEQ);
			 Integer iseq   = new Integer(seq);
			 SqlColumn x = (SqlColumn) allColumns.get(pkeystr);
			 x.setKey(true);
			 pk.put(iseq,x);
		   }
		   pkeys.close();
		   primaryKeys = pk.getOrderedValues();
		   
		   // if no primary key exists then get the first unique index
		   if (primaryKeys.size() == 0) {
			   getFirstUniqueIndex(dbmd);
		   }
//		   if(primaryKeys.size() == 0){
//			   //
//			   System.out.println("error no primary key,no unique index for table: " + this.getTable());
//			   primaryKeys.addAll(this.allColumns.values());
//		   }
		   
}
public void addColumn(SqlColumn newColumn) {
	allColumns.put(newColumn.getColname(), newColumn);
	sqlColumns.add(newColumn);
	if (newColumn.getColname().equalsIgnoreCase("version"))
		hasVersion = true;
	String attType = newColumn.getAttType();
	if (Functions.hasMask(attType, "java.")) {
	    if (!typeList.contains(attType)) {
	    	typeList.add(attType);
	    }
	}
}



/**
 * @param newColumn
 */
private void determineTestableColumn() {
	
	hasTestableColumn = new Boolean (false);
	int numColumns = getSqlColumns().size();
	for (int i=0;i<numColumns;i++) {
		SqlColumn newColumn = (SqlColumn) getSqlColumns().get(i);
		if (!newColumn.isKey()) {
		   	  String coltypname = newColumn.getColtypname();
		   	  if (Functions.hasMask(coltypname,"char")) {
		   	  	hasTestableColumn = new Boolean(true);
		   	  	testableColumn = newColumn;
		   	  	break;
		   	  }
		   }
	}
	 
	 
}
/**
 * This method was created in VisualAge.
 */
private boolean getTableInfoFromDatabase()  {

	initHolders();
	
	try {

		DatabaseMetaData dbmd = ThreadContext.getCurrentContext().getDbconn().getConn().getMetaData();

		ResultSet columns =
			dbmd.getColumns(null, schema, table, null);
		
//		ResultSet theTable = dbmd.getTables(null, schema, table, null);
//		while(theTable.next()){
//			System.out.println(theTable.getString("CAT_NAME"));
//			System.out.println(theTable.getString("TABLE_NAME"));
//		}

		int colcount = 0;

		String COLUMN_NAME 		= "COLUMN_NAME";
		String DATA_TYPE   		= "DATA_TYPE";
		String TYPE_NAME   		= "TYPE_NAME";
		String COLUMN_SIZE 		= "COLUMN_SIZE";
		String DECIMAL_DIGITS 	= "DECIMAL_DIGITS";
		String NULLABLE 		= "NULLABLE";
		String COLUMN_DEF 		= "COLUMN_DEF";
		String COMMENT 		    = "REMARKS";

		while (columns.next()) {
			++colcount;
			String colname = columns.getString(COLUMN_NAME);
			short coltype = columns.getShort(DATA_TYPE);
			int colsize = columns.getInt(COLUMN_SIZE);
			int digits = 0;
			String coltypname = columns.getString(TYPE_NAME).toUpperCase();
			boolean nullable = false;
			boolean withDefault = true;
			String comment = columns.getString(COMMENT);

			if (coltype == Types.DECIMAL || coltype == Types.NUMERIC) {
				digits = columns.getInt(DECIMAL_DIGITS);
			}


			if	(columns.getInt(NULLABLE) == DatabaseMetaData.columnNullable)
				nullable = true;
			else
				nullable = false;
			withDefault = (columns.getString(COLUMN_DEF) != null);
			SqlColumn newColumn =
				new SqlColumn(
					colname,
					colname,
					coltype,
					colsize,
					digits,
					coltypname,
					nullable,
					withDefault,
					comment);
			addColumn(newColumn);
		}

		columns.close();

		// get index information
		getIndexInfo(dbmd);

		// get Foreign Keys
		getImportedKeys(dbmd);
		getExportedKeys(dbmd);
		
		if (colcount > 0)
			return true;
		else
			return false;
			
	} catch (SQLException e) {
		System.out.println("Exception encountered in getTableInfo(): + e.getMessage()");
		throw new RuntimeException(e);
	}
}
	/**
	 * @return Returns the allColumns.
	 */
	public HashMap getAllColumns() {
		return allColumns;
	}
	/**
	 * @return Returns the primaryKeys.
	 */
	public List getPrimaryKeys() {
		return primaryKeys;
	}
	public SqlColumn getPrimaryKey(int i) {
		if (i > getPrimaryKeys().size()-1)
			return null;
		return (SqlColumn) getPrimaryKeys().get(i);
	}
	/**
	 * @return Returns the schema.
	 */
	public String getSchema() {
		return schema;
	}
	/**
	 * @return Returns the sqlColumns.
	 */
	public List getSqlColumns() {
		return sqlColumns;
	}
	/**
	 * @return Returns the table.
	 */
	public String getTable() {
		return table;
	}
	/**
	 * @return Returns the hasTestableColumn.
	 */
	public boolean getHasTestableColumn() {
		if (hasTestableColumn == null)
			determineTestableColumn();
		
		return hasTestableColumn.booleanValue();
	}
	public boolean getHasVersion() {
		return hasVersion;
	}
	public boolean getHasCompositeKey() {
		return getPrimaryKeys().size() > 1;
	}
	public boolean getHasSingleKey() {
		return (getPrimaryKeys().size()) == 1;
	}
	public boolean getHasGeneratedKey() {
		boolean isGenerated = ApplicationProperties.isGeneratedKey(this);
		return isGenerated;
	}
	/**
	 * @return Returns the testableColumn.
	 */
	public SqlColumn getTestableColumn() {
		return testableColumn;
	}
	/**
	 * @return Returns the javaIncludes.
	 */
	public List getTypeList() {
		/*
		System.out.println("typeList: "+typeList.size());
		if (typeList.size() > 0) {
			for (int i=0;i<typeList.size();i++) {
				System.out.println("typeList: "+i+ ((String)typeList.get(i)));
			}
		}
		**/
		return typeList;
		
	}
	/**
	 * @return Returns the entityName.
	 */
	public String getEntityName() {
		if (entityName == null) {
			// get from ApplicationProperties 
			// if not in application properties default to orignal tablename
			ListHashtable pojoNames = ApplicationProperties.getPojoNames();
			if (pojoNames.containsKey(table)) {
				return (String) pojoNames.get(table);
			}
			else
				return entityNameFromTableName;
		}
		else 
			return entityName;
	}
	
	
	public String getPackagePrefix()
	{
	    String puInfo = (String)ApplicationProperties.getPUMap().get(table);
	    if(puInfo != null)
	    {
    	    int index = puInfo.indexOf('^');
    	    String component = puInfo.substring(0, index);
    	    return "com.zte." + ApplicationProperties.getFramework() + "." + component;
	    }
	    else
	    {
		return ApplicationProperties.getProperty("packagePrefix");
		
	    }
	    
	}
	
	public String getPackageModot()
	{
	    String puInfo = (String)ApplicationProperties.getPUMap().get(table);
	    if(puInfo != null)
	    {
	    int index = puInfo.indexOf('^');
	    String pu = puInfo.substring(index + 1);
	    return "." + pu;
	    }
	    else{
		return  ApplicationProperties.getProperty("packageModot");
	    }
	}
	
	public EzwGen getEzwGen() {
		return EzwGen.getEzwGen(this);
	}
	/**
	 * @return Returns the generated.
	 */
	public boolean isGenerated() {
		return generated;
	}
	public boolean getHasImportedKeyColumn(String aColumn) {
		// check whether a column is an imported key column
		boolean isFound = getImportedKeys().getHasImportedKeyColumn(aColumn);
		return  isFound;
	}
	public ForeignKey getImportedKeyParentColumn(String aColumn) {
		// retrieve foreign key given a parent col name
		ForeignKey aKey = getImportedKeys().getImportedKeyParentColumn(aColumn);
		return  aKey;
	}
	/**
	 * @param generated The generated to set.
	 */
	public void setGenerated(boolean generated) {
		this.generated = generated;
	}
	/**
	 * This method was created in VisualAge.
	 */
	private void getImportedKeys(DatabaseMetaData dbmd) throws java.sql.SQLException {
		
			   // get imported keys a
	
			   ResultSet fkeys = dbmd.getImportedKeys(null,schema,table);

			   while ( fkeys.next()) {
				 String pktable = fkeys.getString(PKTABLE_NAME);
				 String pkcol   = fkeys.getString(PKCOLUMN_NAME);
				 String fktable = fkeys.getString(FKTABLE_NAME);
				 String fkcol   = fkeys.getString(FKCOLUMN_NAME);
				 String seq     = fkeys.getString(KEY_SEQ);
				 Integer iseq   = new Integer(seq);
				 getImportedKeys().addForeignKey(pktable,pkcol,fkcol,iseq);
			   }
			   fkeys.close();
	}
	/**
	 * This method was created in VisualAge.
	 */
	private void getExportedKeys(DatabaseMetaData dbmd) throws java.sql.SQLException {
		
			   // get Exported keys
	
			   ResultSet fkeys = dbmd.getExportedKeys(null,schema,table);
			  
			   while ( fkeys.next()) {
				 String pktable = fkeys.getString(PKTABLE_NAME);
				 String pkcol   = fkeys.getString(PKCOLUMN_NAME);
				 String fktable = fkeys.getString(FKTABLE_NAME);
				 String fkcol   = fkeys.getString(FKCOLUMN_NAME);
				 String seq     = fkeys.getString(KEY_SEQ);
				 Integer iseq   = new Integer(seq);
				 getExportedKeys().addForeignKey(fktable,fkcol,pkcol,iseq);
			   }
			   fkeys.close();
	}
	/**
	 * @return Returns the exportedKeys.
	 */
	public ForeignKeys getExportedKeys() {
		if (exportedKeys == null) {
			exportedKeys = new ForeignKeys(this);
		}
		return exportedKeys;
	}
	/**
	 * @return Returns the importedKeys.
	 */
	public ForeignKeys getImportedKeys() {
		if (importedKeys == null) {
			importedKeys = new ForeignKeys(this);
		}
		return importedKeys;
	}
	private void initForeignKeyTables(List ftable,String sch, boolean isCaseSensitive, boolean fromDb) {
		ListHashtable sqlTables = ApplicationProperties.getSqlTables();
		//  loop through all foreign keys and init foreign key tables
		int numFkeys = ftable.size();
		for (int i=0;i<numFkeys;i++) {
			String ftbl = (String) ftable.get(i);
			if (!sqlTables.containsKey(ftbl)) {
                String entityName = (String)ApplicationProperties.getPojoNames().get(ftbl);
                SqlTable foreign = new SqlTable(ftbl, sch,entityName,isCaseSensitive,fromDb);
			}
		}
	}
	public boolean getHasImportedKeyParentColumn(String aColumn) {
		// check whether a column is an imported key column
		boolean isFound = getImportedKeys().getHasImportedKeyParentColumn(aColumn);
		return  isFound;
	}
	/**
	 * @return Returns the entityNameFromTableName.
	 */
	public String getEntityNameFromTableName() {
		return entityNameFromTableName;
	}
	/**
	 * @param entityName The entityName to set.
	 */
	public void setEntityName(String entityName) {
		this.entityName = entityName;
	}
}
