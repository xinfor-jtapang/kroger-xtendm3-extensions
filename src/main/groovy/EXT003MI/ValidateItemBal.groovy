/**
* @Name: EXT003MI.ValidateItemBal
* @Type : ExtendM3Transaction
* @Description: Calculate and validate component stock balance
* @Authors: Jonard Tapang
*
* @CHANGELOGS
*  Version   Date      User            Description
*  1.0.0     20240925  JTAPANG         Initial Release
*/
 
public class ValidateItemBal extends ExtendM3Transaction {
  private final MIAPI mi
  private final ProgramAPI program
  private final DatabaseAPI database
  private final LoggerAPI logger
  
  int inCONO
  String inMFNO, inFACI, inPRNO
  private double inMAQA = 0D
  private double vORQA = 0D
   
  public ValidateItemBal(MIAPI mi, ProgramAPI program, DatabaseAPI database, LoggerAPI logger) {
    this.mi = mi
    this.program = program
    this.database = database
    this.logger = logger
  }
  
  public void main() {
    inCONO = mi.inData.get("CONO") == null ? 0 : mi.inData.get("CONO") as Integer
	  inMFNO = mi.inData.get("MFNO") == null ? "" : mi.inData.get("MFNO") as String
    inFACI = mi.inData.get("FACI") == null ? "" : mi.inData.get("FACI") as String
    inPRNO = mi.inData.get("PRNO") == null ? "" : mi.inData.get("PRNO") as String
    inMAQA = mi.inData.get("MAQA") == null ? 0 : mi.inData.get("MAQA") as Double
    ArrayList<String[]> listResult = new ArrayList<String[]>()
    Iterator<String[]> listResultIterator = null
    
    // Check Company
    if(inCONO == 0){
      inCONO = (Integer) program.LDAZD.CONO
    }
    
    // Check Facility
    if(inFACI.isBlank()){
      inFACI = program.LDAZD.FACI
    }
    
    // Validate inputs
    if (!this.isValidInput()) {
      return
    }
    
    //Get ORQA in MWOHED
    DBAction queryMWOHED = database.table("MWOHED").index("00").selection("VHORQA").build()
    DBContainer conMWOHED = queryMWOHED.getContainer()
    conMWOHED.set("VHCONO", inCONO)
    conMWOHED.set("VHFACI", inFACI)
    conMWOHED.set("VHPRNO", inPRNO)
    conMWOHED.set("VHMFNO", inMFNO)
    
    if (!queryMWOHED.read(conMWOHED)) {
      mi.error("Manufacturing order number ${inMFNO} does not exist")
      return
    } else {
      vORQA = conMWOHED.getDouble("VHORQA")
    }
    
    boolean insufficientBalFlag = false
    
    //Get MWOMAT Record
    DBAction queryMWOMAT = database.table("MWOMAT").index("00").selection("VMFACI", "VMMFNO", "VMMTNO", "VMWHLO", "VMWHSL", "VMREQT", "VMSPMT").build()
    DBContainer conMWOMAT = queryMWOMAT.getContainer()
	  conMWOMAT.set("VMCONO", inCONO)
    conMWOMAT.set("VMFACI", inFACI)
    conMWOMAT.set("VMPRNO", inPRNO)
    conMWOMAT.set("VMMFNO", inMFNO)
    int nrOfRecords = mi.getMaxRecords() <= 0 || mi.getMaxRecords() >= 10000? 10000: mi.getMaxRecords()
    
    Closure<?> resultHandlerMWOMAT  = { DBContainer recordMWOMAT -> 
      String vmFACI = recordMWOMAT.get("VMFACI").toString()
      String vmMFNO = recordMWOMAT.get("VMMFNO").toString()
      String vmMTNO = recordMWOMAT.get("VMMTNO").toString()
      String vmWHLO = recordMWOMAT.get("VMWHLO").toString()
      String vmWHSL = recordMWOMAT.get("VMWHSL").toString()
      int vmSPMT = recordMWOMAT.getInt("VMSPMT")
      Double vmREQT = Double.parseDouble(recordMWOMAT.get("VMREQT").toString())
      
      if((vmSPMT == 3 || vmSPMT == 4) && !insufficientBalFlag) {
        Double totalSTQT = 0d
        Double totalALQT = 0d
        
		    //Get MITLOC Record
        DBAction queryMITLOC = database.table("MITLOC").index("00").selection("MLSTQT", "MLALQT", "MLSTAS").build()
        DBContainer conMITLOC = queryMITLOC.getContainer()
        conMITLOC.set("MLCONO", inCONO)
        conMITLOC.set("MLWHLO", vmWHLO)
        conMITLOC.set("MLITNO", vmMTNO)
        conMITLOC.set("MLWHSL", vmWHSL)
  
        Closure<?> processMITLOCRecord = { DBContainer recordMITLOC ->
          if(Integer.parseInt(recordMITLOC.get("MLSTAS").toString()) == 2) {
            Double mlSTQT = Double.parseDouble(recordMITLOC.get("MLSTQT").toString())
            Double mlALQT = Double.parseDouble(recordMITLOC.get("MLALQT").toString())
			
            totalSTQT = totalSTQT + mlSTQT
            totalALQT = totalALQT + mlALQT
            
          }
        }
        queryMITLOC.readAll(conMITLOC, 4, nrOfRecords, processMITLOCRecord)
        
        Double vAVQT = totalSTQT - totalALQT
        Double vCQEP = inMAQA * (vmREQT / vORQA)
        
        if(vAVQT < vCQEP) {
          insufficientBalFlag = true
          mi.error("Negative inventory will be created insufficient balance for Backflush items.")
          return false
        }
	    }
	  }  
	  queryMWOMAT.readAll(conMWOMAT, 4, nrOfRecords, resultHandlerMWOMAT)
  }

  /**
   * Validate input fields
  */
  boolean isValidInput() {
    // Check Product number
    if (!inPRNO.isBlank()) {
      if (!this.checkPRNO()) {
        mi.error("Product number ${inPRNO} does not exist")
        return false
      }
    }
    
    // Check Facility
    if (!inFACI.isBlank()) {
      if (!this.checkFACI()) {
        mi.error("Facility ${inFACI} does not exist")
        return false
      }
    }

    return true
  }
  
  /**
   * Validate ITNO from MITMAS
  */
  boolean checkPRNO() {
    DBAction queryMITMAS = database.table("MITMAS").index("00").build()
    DBContainer conMITMAS = queryMITMAS.getContainer()
    conMITMAS.set("MMCONO", inCONO)
    conMITMAS.set("MMITNO", inPRNO)
  
    if (!queryMITMAS.read(conMITMAS)) {
      return false
    } else {
      return true
    }
  }
  
  /**
   * Validate FACI from CFACIL
  */
  boolean checkFACI() {
    DBAction queryCFACIL = database.table("CFACIL").index("00").build()
    DBContainer conCFACIL = queryCFACIL.getContainer()
    conCFACIL.set("CFCONO", inCONO)
    conCFACIL.set("CFFACI", inFACI)
  
    if (!queryCFACIL.read(conCFACIL)) {
      return false
    } else {
      return true
    }
  }

}