/**
 * @Name: EXT002MI.Del
 * @Description: Deletes MITSTA record/s
 * @Authors: Jonard Tapang
 *
 *  @CHANGELOGS
 *  Version   Date(YMd8)    User        Description
 *  1.0.0     20241014      JTAPANG     Initial Release
 */
 
public class Del extends ExtendM3Transaction {
  private final MIAPI mi
  private final ProgramAPI program
  private final DatabaseAPI database
  
  int inCONO
  String inWHLO, inITNO
  
  public Del(MIAPI mi, ProgramAPI program, DatabaseAPI database) {
    this.mi = mi
    this.program = program
    this.database = database
  }
  
  public void main() {
    // Initialize input fields
    inCONO = mi.inData.get("CONO") == null ? 0 : mi.inData.get("CONO") as Integer
    inWHLO = mi.inData.get("WHLO") == null ? "" : mi.inData.get("WHLO") as String
    inITNO = mi.inData.get("ITNO") == null ? "" : mi.inData.get("ITNO") as String
    
    // Validate inputs
    if (!this.isValidInput()) {
      return
    }
    
    // Check if exists
    DBAction queryMITSTA = database.table("MITSTA").index("00").build()
    DBContainer conMITSTA = queryMITSTA.getContainer()
    conMITSTA.set("MHCONO", inCONO)
    conMITSTA.set("MHWHLO", inWHLO)
    int nrOfKeys = 2
    if (!inITNO.isBlank()) {
      conMITSTA.set("MHITNO", inITNO)
      nrOfKeys++
    }
    
    Closure < ? > deleteCallback = {
    LockedResult lockedResult ->
    lockedResult.delete()
    }
    
    if (!queryMITSTA.readAllLock(conMITSTA, nrOfKeys, deleteCallback)) 
    {
      mi.error("The record does not exist")
    }
  }
  
  /**
   * Validate input fields
   */
  boolean isValidInput() {
    // Check Company
    if (inCONO != 0) {
      if (!this.checkCONO()) {
        mi.error("Company ${inCONO} does not exist");
        return false;
      }
    } else {
        mi.error("Company ${inCONO} is invalid");
        return false;
    }
    
    // Check Warehouse
    if (!inWHLO.isBlank()) {
      if (!this.checkWHLO()) {
        mi.error("Warehouse ${inWHLO} does not exist");
        return false;
      }
    }
    
    // Check Item number
    if (!inITNO.isBlank()) {
      if (!this.checkITNO()) {
        mi.error("Item number ${inITNO} does not exist");
        return false;
      }
    }
    
    return true;
  }
  
  /**
   * Validate CONO from CMNCMP
   */
  boolean checkCONO() {
    DBAction queryCMNCMP = database.table("CMNCMP").index("00").build();
    DBContainer conCMNCMP = queryCMNCMP.getContainer();
    conCMNCMP.set("JICONO", inCONO);
  
    if (!queryCMNCMP.read(conCMNCMP)) {
      return false;
    } else {
      return true;
    }
  }
  
  /**
   * Validate WHLO from MITWHL
   */
  boolean checkWHLO() {
    DBAction queryMITWHL = database.table("MITWHL").index("00").build();
    DBContainer conMITWHL = queryMITWHL.getContainer();
    conMITWHL.set("MWCONO", inCONO);
    conMITWHL.set("MWWHLO", inWHLO);
  
    if (!queryMITWHL.read(conMITWHL)) {
      return false;
    } else {
      return true;
    }
  }
  
  /**
   * Validate ITNO from MITMAS
   */
  boolean checkITNO() {
    DBAction queryMITMAS = database.table("MITMAS").index("00").build();
    DBContainer conMITMAS = queryMITMAS.getContainer();
    conMITMAS.set("MMCONO", inCONO);
    conMITMAS.set("MMITNO", inITNO);
  
    if (!queryMITMAS.read(conMITMAS)) {
      return false;
    } else {
      return true;
    }
  }
  
}