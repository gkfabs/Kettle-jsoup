 /* Copyright (c) 2007 Pentaho Corporation.  All rights reserved. 
 * This software was developed by Pentaho Corporation and is provided under the terms 
 * of the GNU Lesser General Public License, Version 2.1. You may not use 
 * this file except in compliance with the license. If you need a copy of the license, 
 * please go to http://www.gnu.org/licenses/lgpl-2.1.txt. The Original Code is Pentaho 
 * Data Integration.  The Initial Developer is Samatar HASSAN.
 *
 * Software distributed under the GNU Lesser Public License is distributed on an "AS IS" 
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or  implied. Please refer to 
 * the license for the specific language governing your rights and limitations.*/

package be.ibridge.kettle.jsoup;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.vfs2.FileObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.ResultFile;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.fileinput.FileInputList;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.vfs.KettleVFS;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;



/**
 * Read Jsoup files, parse them and convert them to rows and writes these to one or more output 
 * streams.
 * 
 * @author Samatar
 * @since 20-06-2010
 */
public class JsoupInput extends BaseStep implements StepInterface  
{
	private static Class<?> PKG = JsoupInputMeta.class; // for i18n purposes, needed by Translator2!!   $NON-NLS-1$

	private JsoupInputMeta meta;
	private JsoupInputData data;

	
	public JsoupInput(StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, TransMeta transMeta, Trans trans)
	{
		super(stepMeta, stepDataInterface, copyNr, transMeta, trans);
	}
   
 
	/**
	 * Build an empty row based on the meta-data.
	 * 
	 * @return
	 */
	private Object[] buildEmptyRow() {
       Object[] rowData = RowDataUtil.allocateRowData(data.outputRowMeta.size());

	    return rowData;
	}
	private void handleMissingFiles() throws KettleException {
		List<FileObject> nonExistantFiles = data.files.getNonExistantFiles();
		if (nonExistantFiles.size() != 0) {
			String message = FileInputList.getRequiredFilesDescription(nonExistantFiles);
			log.logError(BaseMessages.getString(PKG, "JsoupInput.Log.RequiredFilesTitle"), BaseMessages.getString(PKG, "JsoupInput.Log.RequiredFiles", message));

			throw new KettleException(BaseMessages.getString(PKG, "JsoupInput.Log.RequiredFilesMissing",message));
		}

		List<FileObject> nonAccessibleFiles = data.files.getNonAccessibleFiles();
		if (nonAccessibleFiles.size() != 0)
		{
			String message = FileInputList.getRequiredFilesDescription(nonAccessibleFiles);
			log.logError(BaseMessages.getString(PKG, "JsoupInput.Log.RequiredFilesTitle"), BaseMessages.getString(PKG, "JsoupInput.Log.RequiredNotAccessibleFiles",message));

				throw new KettleException(BaseMessages.getString(PKG, "JsoupInput.Log.RequiredNotAccessibleFilesMissing",message));
		}
	}
   private boolean ReadNextString()
   {
	   
	   try {
		   data.readrow= getRow();  // Grab another row ...
		   
		   if (data.readrow==null)  {
			   // finished processing!
           	   if (log.isDetailed()) logDetailed(BaseMessages.getString(PKG, "JsoupInput.Log.FinishedProcessing"));
               return false;
           }
 
		   if(first) {
			    first=false;
			    
				data.inputRowMeta = getInputRowMeta();
	            data.outputRowMeta = data.inputRowMeta.clone();
	            meta.getFields(data.outputRowMeta, getStepname(), null, null, this);
	            
	            // Get total previous fields
	            data.totalpreviousfields=data.inputRowMeta.size();

				// Create convert meta-data objects that will contain Date & Number formatters
	            data.convertRowMeta = data.outputRowMeta.clone();
	            for (int i=0;i<data.convertRowMeta.size();i++) data.convertRowMeta.getValueMeta(i).setType(ValueMetaInterface.TYPE_STRING);
	  
	            // For String to <type> conversions, we allocate a conversion meta data row as well...
				//
				data.convertRowMeta = data.outputRowMeta.clone();
				for (int i=0;i<data.convertRowMeta.size();i++) {
					data.convertRowMeta.getValueMeta(i).setType(ValueMetaInterface.TYPE_STRING);            
				}
				
				// Check if source field is provided
				if (Const.isEmpty(meta.getFieldValue())){
					logError(BaseMessages.getString(PKG, "JsoupInput.Log.NoField"));
					throw new KettleException(BaseMessages.getString(PKG, "JsoupInput.Log.NoField"));
				}
				
				// cache the position of the field			
				if (data.indexSourceField<0) {	
					data.indexSourceField =getInputRowMeta().indexOfValue(meta.getFieldValue());
					if (data.indexSourceField<0)
					{
						// The field is unreachable !
						logError(BaseMessages.getString(PKG, "JsoupInput.Log.ErrorFindingField", meta.getFieldValue())); //$NON-NLS-1$ //$NON-NLS-2$
						throw new KettleException(BaseMessages.getString(PKG, "JsoupInput.Exception.CouldnotFindField",meta.getFieldValue())); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
			
			}
		   
	   
		   // get source field value
		   String fieldValue= getInputRowMeta().getString(data.readrow,data.indexSourceField);
			
		   if(log.isDetailed()) logDetailed(BaseMessages.getString(PKG, "JsoupInput.Log.SourceValue", meta.getFieldValue(),fieldValue));

		   if(meta.getIsAFile())  {
			   
				// source is a file.
	    		data.file = KettleVFS.getFileObject(fieldValue, getTransMeta());
	    		if(meta.isIgnoreEmptyFile() && data.file.getContent().getSize()==0) {
	    			// log only basic as a warning (was before logError)
	    			logBasic(BaseMessages.getString(PKG, "JsoupInput.Error.FileSizeZero", data.file.getName()));
	    			ReadNextString();
	    		} 
		   } else {
			    // read string
			    data.stringToParse = fieldValue;
		   }
		   
		   readFileOrString();
	   } catch(Exception e) {
			logError(BaseMessages.getString(PKG, "JsoupInput.Log.UnexpectedError", e.toString()));
			stopAll();
			logError(Const.getStackTracker(e));
			setErrors(1);
			return false;
		}
		return true;
	   
   }
   private void addFileToResultFilesname(FileObject file) throws Exception {
       if(meta.addResultFile()) {
			// Add this to the result file names...
			ResultFile resultFile = new ResultFile(ResultFile.FILE_TYPE_GENERAL, file, getTransMeta().getName(), getStepname());
			resultFile.setComment(BaseMessages.getString(PKG, "JsoupInput.Log.FileAddedResult"));
			addResultFile(resultFile);
       }
   }

	private boolean openNextFile() {
		try {
            if (data.filenr>=data.files.nrOfFiles()) {
            	if (log.isDetailed()) logDetailed(BaseMessages.getString(PKG, "JsoupInput.Log.FinishedProcessing"));
                return false;
            }
            // Close previous file if needed
            if(data.file!=null) data.file.close();
            // get file
    		data.file = (FileObject) data.files.getFile(data.filenr);
    		if(meta.isIgnoreEmptyFile() && data.file.getContent().getSize()==0) {
    			// log only basic as a warning (was before logError)
    			logBasic(BaseMessages.getString(PKG, "JsoupInput.Error.FileSizeZero", ""+data.file.getName()));
    			openNextFile();
    		}
    		readFileOrString();
		} catch(Exception e) {
			logError(BaseMessages.getString(PKG, "JsoupInput.Log.UnableToOpenFile", ""+data.filenr, data.file.toString(), e.toString()));
			stopAll();
			setErrors(1);
			return false;
		}
		return true;
	}
	
	private void readFileOrString() throws Exception {
		if(data.file!=null) {
			data.filename =  KettleVFS.getFilename(data.file);
			// Add additional fields?
			if (meta.getShortFileNameField()!=null && meta.getShortFileNameField().length()>0) {
				data.shortFilename  =  data.file.getName().getBaseName();
			}
			if (meta.getPathField()!=null && meta.getPathField().length()>0) {
				data.path = KettleVFS.getFilename(data.file.getParent());
			}
			if (meta.isHiddenField()!=null && meta.isHiddenField().length()>0)  {
				data.hidden =  data.file.isHidden();
			}
			if (meta.getExtensionField()!=null && meta.getExtensionField().length()>0) {
				data.extension =  data.file.getName().getExtension();
			}
			if (meta.getLastModificationDateField()!=null && meta.getLastModificationDateField().length()>0) {
				data.lastModificationDateTime =  new Date(data.file.getContent().getLastModifiedTime());
			}
			if (meta.getUriField()!=null && meta.getUriField().length()>0) {
				data.uriName = data.file.getName().getURI();
			}
			if (meta.getRootUriField()!=null && meta.getRootUriField().length()>0) {
				data.rootUriName = data.file.getName().getRootURI();
			}
			// Check if file is empty
			long fileSize= data.file.getContent().getSize();	
	
			if (meta.getSizeField()!=null && meta.getSizeField().length()>0) {
				data.size = fileSize;
			}
			// Move file pointer ahead!
			data.filenr++;

			if (log.isDetailed()) logDetailed(BaseMessages.getString(PKG, "JsoupInput.Log.OpeningFile", data.file.toString()));
	
			addFileToResultFilesname(data.file);
		}

		parseJsoup();

	}
	private void parseJsoup() throws Exception {
		
		// Read JSOUP source
		if(data.file!=null) {
			data.jsoupReader = Jsoup.parse(new File(data.filename), "UTF-8");
		}else {
			if(meta.isReadUrl()){
				data.jsoupReader = Jsoup.parse(new URL(data.stringToParse), 1000);
			}else {
				// read string
				data.jsoupReader = Jsoup.parse(data.stringToParse);
			}
		}
		List<Elements> resultList = new ArrayList<Elements>();
		data.nrrecords=-1;
		data.recordnr=0;
		String prevPath="";
		for(int i =0; i<data.nrInputFields; i++) {
			String path = meta.getInputFields()[i].getPath();
			Elements ja =data.jsoupReader.select(path);
			if(ja.size() > 0 && (data.nrrecords!=-1 && data.nrrecords!= ja.size() && ja != null)) {
				throw new KettleException(BaseMessages.getString(PKG, "JsoupInput.Error.BadStructure", ja.size(), path, prevPath, data.nrrecords));
			}
			resultList.add(ja);
			if(data.nrrecords==-1 && ja != null) {
				data.nrrecords=ja.size();
			}
			prevPath=path;
		}

		data.resultList=new ArrayList<Elements>();
			
		Iterator<Elements> it=resultList.iterator();

		while(it.hasNext()) {
			Elements j= it.next();
			if(j == null || j.size() == 0) {
				if(data.nrrecords==-1) {
					data.nrrecords=1;
				}
				// The object is empty means that we do not
				// find Jsoup path
				// We need here to create a dummy structure
                j = new Elements();
				for(int i=0; i<data.nrrecords; i++){
                    j.add(null);
				}
			}
            data.resultList.add(j);
		}
		resultList=null;
		
        if (log.isDetailed())  {
            logDetailed(BaseMessages.getString(PKG, "JsoupInput.Log.NrRecords",data.nrrecords));
        } 
        
	}
	public boolean processRow(StepMetaInterface smi, StepDataInterface sdi) throws KettleException {
		if(first && !meta.isInFields()) {
			first=false;
			
			data.files = meta.getFiles(this);
		
			if(!meta.isdoNotFailIfNoFile() && data.files.nrOfFiles()==0) {
				throw new KettleException(BaseMessages.getString(PKG, "JsoupInput.Log.NoFiles"));
			}

			handleMissingFiles();
			
			// Create the output row meta-data
            data.outputRowMeta = new RowMeta();

			meta.getFields(data.outputRowMeta, getStepname(), null, null, this);
			
			// Create convert meta-data objects that will contain Date & Number formatters
            data.convertRowMeta = data.outputRowMeta.clone();
            for (int i=0;i<data.convertRowMeta.size();i++) data.convertRowMeta.getValueMeta(i).setType(ValueMetaInterface.TYPE_STRING);
  
            // For String to <type> conversions, we allocate a conversion meta data row as well...
			//
			data.convertRowMeta = data.outputRowMeta.clone();
			for (int i=0;i<data.convertRowMeta.size();i++) {
				data.convertRowMeta.getValueMeta(i).setType(ValueMetaInterface.TYPE_STRING);            
			}
		}
		Object[] r=null;
		try {
			 // Grab a row
			r=getOneRow();
			if (r==null)  {
				setOutputDone();  // signal end to receiver(s)
				return false; // end of data or error.
		    }
			 
			if (log.isRowLevel()) logRowlevel(BaseMessages.getString(PKG, "JsoupInput.Log.ReadRow", data.outputRowMeta.getString(r)));
			incrementLinesInput();
			data.rownr++;
			 
			putRow(data.outputRowMeta, r);  // copy row to output rowset(s);
			 
			 if (meta.getRowLimit()>0 && data.rownr>meta.getRowLimit()) {
				 // limit has been reached: stop now.
		          setOutputDone();
		          return false;
		     }	
			  
		} catch(Exception e) {
			boolean sendToErrorRow=false;
			String errorMessage=null;
			if (getStepMeta().isDoingErrorHandling()) {
                sendToErrorRow = true;
                errorMessage = e.toString();
	        } else {
				logError(BaseMessages.getString(PKG, "JsoupInput.ErrorInStepRunning",e.getMessage())); //$NON-NLS-1$
				setErrors(1);
				stopAll();
				setOutputDone();  // signal end to receiver(s)
				return false;
			}
			if (sendToErrorRow)  {
				 // Simply add this row to the error row
				putError(getInputRowMeta(), r, 1, errorMessage, null, "JsoupInput001");
	         }
			
		}
		return true;
	}
	
	private Object[] getOneRow()  throws KettleException {

		if(!meta.isInFields()) {
			while ((data.recordnr>=data.nrrecords || data.file==null)) {
		        if (!openNextFile()) {
		            return null;
		        }
			} 
		} else {
			while ((data.recordnr>=data.nrrecords || data.readrow==null)) {
				if(!ReadNextString()) {
					return null;
				}
				if(data.readrow==null) {
					return null;
				}
			}
		}
		
		return buildRow();
	}


		
	private Object[] buildRow() throws KettleException {
		// Create new row...
		Object[] outputRowData = buildEmptyRow();

		if(data.readrow!=null) outputRowData = data.readrow.clone();

		// Read fields...
		for (int i=0;i<data.nrInputFields;i++) {	
			// Get field
			JsoupInputField field = meta.getInputFields()[i];

			// get jsoup array for field
			Elements jsoupa=data.resultList.get(i);
			String nodevalue=null;
			if(jsoupa!=null) {
				Element jo= jsoupa.get(data.recordnr);
				if(jo!=null){

                    // Do Element Type
                    switch (field.getElementType()) {
                    case JsoupInputField.ELEMENT_TYPE_NODE:
                        // Do Result Type
                        switch (field.getResultType()) {
                        case JsoupInputField.RESULT_TYPE_TEXT:
                            nodevalue = jo.text();
                            break;
                        case JsoupInputField.RESULT_TYPE_TYPE_OUTER_HTML:
                            nodevalue = jo.outerHtml();
                            break;
                        case JsoupInputField.RESULT_TYPE_TYPE_INNER_HTML:
                            nodevalue = jo.html();
                            break;
                        default:
                            nodevalue = jo.toString();
                            break;
                        }
                        break;
                    case JsoupInputField.ELEMENT_TYPE_ATTRIBUT:
                        nodevalue = jo.attr(field.getAttribute());
                        break;
                    default:
                        nodevalue = jo.toString();
                        break;
                    }
                }
            }


			// Do trimming
			switch (field.getTrimType()) {
				case JsoupInputField.TYPE_TRIM_LEFT:
					nodevalue = Const.ltrim(nodevalue);
					break;
				case JsoupInputField.TYPE_TRIM_RIGHT:
					nodevalue = Const.rtrim(nodevalue);
					break;
				case JsoupInputField.TYPE_TRIM_BOTH:
					nodevalue = Const.trim(nodevalue);
					break;
				default:
					break;
			}
			
			if(meta.isInFields()) {
				// Add result field to input stream
                outputRowData = RowDataUtil.addValueData(outputRowData,data.totalpreviousfields+i, nodevalue);
			}
			// Do conversions
			//
			ValueMetaInterface targetValueMeta = data.outputRowMeta.getValueMeta(data.totalpreviousfields+i);
			ValueMetaInterface sourceValueMeta = data.convertRowMeta.getValueMeta(data.totalpreviousfields+i);
			outputRowData[data.totalpreviousfields+i] = targetValueMeta.convertData(sourceValueMeta, nodevalue);

			// Do we need to repeat this field if it is null?
			if (meta.getInputFields()[i].isRepeated())  {
				if (data.previousRow!=null && Const.isEmpty(nodevalue)) {
					outputRowData[data.totalpreviousfields+i] = data.previousRow[data.totalpreviousfields+i];
				}
			}
		}// End of loop over fields...	
		
		int rowIndex = data.nrInputFields;
		
		// See if we need to add the filename to the row...
		if ( meta.includeFilename() && !Const.isEmpty(meta.getFilenameField()) ) {
			outputRowData[rowIndex++] = data.filename;
		}
		 // See if we need to add the row number to the row...  
        if (meta.includeRowNumber() && !Const.isEmpty(meta.getRowNumberField())) {
            outputRowData[rowIndex++] = new Long(data.rownr);
        }
        // Possibly add short filename...
		if (meta.getShortFileNameField()!=null && meta.getShortFileNameField().length()>0) {				
			outputRowData[rowIndex++] = data.shortFilename;
		}
		// Add Extension
		if (meta.getExtensionField()!=null && meta.getExtensionField().length()>0) {
			outputRowData[rowIndex++] = data.extension;
		}
		// add path
		if (meta.getPathField()!=null && meta.getPathField().length()>0) {
			outputRowData[rowIndex++] = data.path;
		}
		// Add Size
		if (meta.getSizeField()!=null && meta.getSizeField().length()>0) {
			outputRowData[rowIndex++] = new Long(data.size);
		}
		// add Hidden
		if (meta.isHiddenField()!=null && meta.isHiddenField().length()>0) {
			outputRowData[rowIndex++] = new Boolean(data.path);
		}
		// Add modification date
		if (meta.getLastModificationDateField()!=null && meta.getLastModificationDateField().length()>0){
			outputRowData[rowIndex++] = data.lastModificationDateTime;
		}
		// Add Uri
		if (meta.getUriField()!=null && meta.getUriField().length()>0) {
			outputRowData[rowIndex++] = data.uriName;
		}
		// Add RootUri
		if (meta.getRootUriField()!=null && meta.getRootUriField().length()>0) {
			outputRowData[rowIndex++] = data.rootUriName;
		}
		data.recordnr++; 
		
		RowMetaInterface irow = getInputRowMeta();
		
		data.previousRow = irow==null?outputRowData:(Object[])irow.cloneRow(outputRowData); // copy it to make
		// surely the next step doesn't change it in between...

		return outputRowData;
	}

	public boolean init(StepMetaInterface smi, StepDataInterface sdi) {
		meta=(JsoupInputMeta)smi;
		data=(JsoupInputData)sdi;				
		
		if (super.init(smi, sdi)) {
			data.rownr = 1L;
			data.nrInputFields=meta.getInputFields().length;
			// Take care of variable substitution
			for(int i =0; i<data.nrInputFields; i++) {
				JsoupInputField field = meta.getInputFields()[i];
				field.setPath(environmentSubstitute(field.getPath()));
			}
			
            // Init a new JSOUP reader
            data.jsoupReader= new Document("");

			return true;
		}
		return false;		
	}
	
	public void dispose(StepMetaInterface smi, StepDataInterface sdi) {
		meta = (JsoupInputMeta) smi;
		data = (JsoupInputData) sdi;
		if(data.file!=null)  {
			try {
				data.file.close();
			}catch (Exception e){}
		}
		data.resultList=null;
		super.dispose(smi, sdi);
	}
}
