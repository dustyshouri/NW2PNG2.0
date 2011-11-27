/*
NW2PNG by Alex (born2kill)
http://forums.graalonline.com/forums/showthread.php?t=134259601

Modifications by Chris Vimes
 */
package src.born2kill.nw2png;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import javax.imageio.ImageIO;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.ImageIcon;

import java.util.regex.Pattern;

public class NW2PNGHelper implements Runnable {
    private BufferedImage tileset = null;
    private double scale = 1;
    private File sourceFile;
    private File outputFile;
    private Listener listener;
    private String graalDir = "C:\\Program Files\\Graal\\",filenamecacheDir,parsingLine;
    private boolean renderinggmap = false,filterOutput = true,splitImages = false,renderNPCs = true,renderChars = true;
    
    int ganiOffsetx = 0;
    int ganiOffsety = 0;
    
    ArrayList<String[]> tiledefs = new ArrayList<String[]>();
    ArrayList<String[]> level_npcs = new ArrayList<String[]>();
    
    public Listener getListener() {
        return listener;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }
    
    public boolean getFilter() {
      return filterOutput;
    }
    
    public void setFilter(boolean filter) {
      this.filterOutput = filter;
    }
    
    public boolean getRenderNPCs() {
      return renderNPCs;
    }
    
    public void setRenderNPCs(boolean filter) {
      this.renderNPCs = filter;
    }
    
    public boolean getRenderChars() {
      return renderChars;
    }
    
    public void setRenderChars(boolean filter) {
      this.renderChars = filter;
    }
    
    public boolean getSplit() {
      return splitImages;
    }
    
    public void setSplit(boolean splitImages) {
      this.splitImages = splitImages;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public String getGraalDir() {
        return graalDir;
    }

    public void setGraalDir(String graalDir) {
        this.graalDir = graalDir;
    }

    public BufferedImage getTileset() {
        return tileset;
    }

    public NW2PNGHelper(Listener listener) {
        setListener(listener);
    }

    public void setTileset(File tilesetFile) throws IOException {
        tileset = ImageIO.read(tilesetFile);
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    public void generate() {
        Thread runner = new Thread(this);
        runner.start();
    }
    
    public void run() {
      parsingLine = "";
      try {
        FileWriter fstream = new FileWriter("errorLog.txt",false);
        BufferedWriter out = new BufferedWriter(fstream);
        out.write("");
        out.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      
      String source_path = getSourceFile().getAbsolutePath();
      tiledefs = new ArrayList<String[]>();
      
      CheckFILENAMECACHE();
      
      Date time = new Date();
      long startTime = time.getTime();
      
      level_npcs.clear();
      
      try {
        if (source_path.endsWith(".nw")) {
          BufferedImage renderNW = renderLevel(getSourceFile());
          if (renderinggmap == false) getListener().sendMessage("Saving image...");
          try {
            File file = getOutputFile();
            ImageIO.write(renderNW, "png", file);
            if (renderinggmap == false) {
              getListener().sendMessage("The image has been saved successfully!");
              time = new Date();
              getListener().sendMessage("Parsed and rendered in " + (int)((time.getTime() - startTime)/1000) + " seconds.");
            }
          } catch (IOException e) {
            getListener().sendMessage("Error: Couldn't save the image");
          }
        } else if (source_path.endsWith(".gmap")) {
          BufferedImage renderGmap = renderGmap(getSourceFile());
          if (renderGmap == null) {
            getListener().sendMessage("The image has been saved successfully!");
            time = new Date();
            getListener().sendMessage("Parsed and rendered in " + (int)((time.getTime() - startTime)/1000) + " seconds.");
            return;
          }
          getListener().sendMessage("Saving image...");
          try {
            File file = getOutputFile();
            ImageIO.write(renderGmap, "png", file);
            getListener().sendMessage("The image has been saved successfully!");
            time = new Date();
            getListener().sendMessage("Parsed and rendered in " + (int)((time.getTime() - startTime)/1000) + " seconds.");
          } catch (IOException e) {
            renderinggmap = false;
            writeLog(e);
            getListener().sendMessage("Error: Couldn't save the image");
          }
        }
        // If Java runs out of memory, let user know and return control
      } catch (OutOfMemoryError e) {
        renderinggmap = false;
        writeLog(e);
        getListener().sendMessage("Error: Out of memory! Try MoreMemory.bat");
        getListener().doneGenerating();
      }
      renderinggmap = false;
      getListener().doneGenerating();
    }

    private BufferedImage renderLevel(File source) {
        String level_name = parsingLine = source.getName();
        String sourcePath = source.getAbsolutePath();
        int intTile = (int) (16 * getScale());
        int intDimension = (int) (intTile*64);
        
        writeLog("Parsing level: " +  parsingLine);
        
        try {
            FileReader level_in = new FileReader(source);
            BufferedReader level_reader = new BufferedReader(level_in);

            BufferedImage gmap_tiles = new BufferedImage(intDimension, intDimension, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D tiles_g2d = gmap_tiles.createGraphics();
            if (getFilter()) tiles_g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            else tiles_g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            BufferedImage gmap_npcs = new BufferedImage(intDimension, intDimension, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D npcs_g2d = gmap_npcs.createGraphics();
            if (getFilter()) npcs_g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            else npcs_g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            ArrayList<String[][]> ganis = new ArrayList<String[][]>();
            ArrayList<String> level_file = new ArrayList<String>();
            int level_highest_layer = 0;
           
            String level_file_line = level_reader.readLine();
            
            // Start scanning NW text for parsing
            while (level_file_line != null) {
                level_file_line = level_file_line.trim();
                parsingLine = level_file_line;
                
                // Ignore any lines commented out.
                if (level_file_line.startsWith("//")) {
                  //System.out.println(level_file_line);
                  level_file_line = level_reader.readLine();
                  continue;
                }
                if (level_file_line.startsWith("BOARD ")) {
                    // Look for Tile data
                    level_file.add(level_file_line);
                    level_file_line = level_file_line.substring(6);
                    level_file_line = level_file_line.substring(level_file_line.indexOf(' ') + 1);
                    level_file_line = level_file_line.substring(level_file_line.indexOf(' ') + 1);
                    level_file_line = level_file_line.substring(level_file_line.indexOf(' ') + 1);

                    int level_current_layer = Integer.parseInt(level_file_line.substring(0, level_file_line.indexOf(' ')));
                    if (level_current_layer > level_highest_layer) {
                        level_highest_layer = level_current_layer;
                    }
                } else if (level_file_line.indexOf("addtiledef2") >= 0 ) {
                    // Look for addtiledef2 commands and add it to the tiledefs ArrayList
                    level_file_line = level_file_line.trim();
                    level_file_line = level_file_line.substring(level_file_line.indexOf("addtiledef2") + 12);
                    
                    level_file_line = level_file_line.replace("(","");
                    level_file_line = level_file_line.replace(")","");
                    level_file_line = level_file_line.replaceAll("\"","");
                    level_file_line = level_file_line.replace("#L",level_name);
                    
                    final String REGEX = ",";
                    Pattern p = Pattern.compile(REGEX);
                    String[] partitems = p.split(level_file_line);
                    
                    String imgname    = partitems[0];
                    String definition = partitems[1];
                    String drawx      = partitems[2];
                    String drawy      = partitems[3].substring(0,partitems[3].indexOf(";"));
                  
                    String[] temparr = {definition,imgname,drawx,drawy};
                    tiledefs.add(temparr);
                } else if (level_file_line.indexOf("addtiledef") >= 0 && level_file_line.indexOf("addtiledef2") < 0) {
                  // Treat it like addtiledef2, except send position 0,0 instead
                  level_file_line = level_file_line.trim();
                  level_file_line = level_file_line.substring(level_file_line.indexOf("addtiledef") + 11);
                  
                  level_file_line = level_file_line.replace("(","");
                  level_file_line = level_file_line.replace(")","");
                  level_file_line = level_file_line.replaceAll("\"","");
                  level_file_line = level_file_line.replace("#L",level_name);
                  
                  final String REGEX = ",";
                  Pattern p = Pattern.compile(REGEX);
                  String[] partitems = p.split(level_file_line);
                  
                  String imgname    = partitems[0];
                  String definition = partitems[1];
                  String[] temparr = {definition,imgname,Integer.toString(0),Integer.toString(0)};
                  tiledefs.add(temparr);
              }  else if (getRenderNPCs() == false) {
                level_file_line = level_reader.readLine();
                continue;
              } else if (level_file_line.startsWith("NPC ")) {
                  // Start parsing NPC
                  double NPCx = 0;
                  double NPCy = 0;
                  String[] parsexy = level_file_line.split("\\s+");
                  NPCx = Integer.parseInt(parsexy[2]);
                  NPCy = Integer.parseInt(parsexy[3]);
                  
                  // Since we're going to scan for the NPC data now set a buffer mark
                  // After parsing the NPC is done, we can reset the buffer to this position
                  // for further parsing. 20480 = 10kb, the limit of which to scan forward
                  level_reader.mark(20480);
                  
                  // Create array to store showcharacter/gani info in
                  // attrs = {gani,#c0,#c1,#c2,#c3,#c4,#P1,#P2,#P3,#1,#2,#3,#8,param1,dir};
                  String[] attrs = new String[15];

                  String npc_imgpart = level_reader.readLine();
                  boolean foundshowcharacter = false,foundgani = false,foundsetimgpart = false,foundignorerender = false;
                  
                  // Loop for parsing NPC script. It will only scan ahead 300 lines, or until it finds NPCEND
                  for (int j = 0; j < 300; j++) {
                    npc_imgpart = npc_imgpart.trim();
                    parsingLine = npc_imgpart;
                    
                    if (npc_imgpart.indexOf("join") > -1) {
                      // If 'setimgpart' is found, append its values to the render data
                      npc_imgpart = npc_imgpart.replaceAll("\"","");
                      npc_imgpart = npc_imgpart.replace("this.","");
                      npc_imgpart = npc_imgpart.replace("("," ");
                      npc_imgpart = npc_imgpart.replace(")","");
                      npc_imgpart = npc_imgpart.replace(";","");
                      String[] tokens = npc_imgpart.split("\\s+");
                      tokens[1] = tokens[1].trim();
                      //getJoin(tokens[1])
                      String newImg = getJoin(tokens[1]);
                      if (newImg != null) {
                        if (newImg.indexOf(":") < 0 && level_file_line.contains("-")) {
                          level_file_line = level_file_line.replace("-",newImg);
                        } else {
                          if (newImg.indexOf(":") > -1) {
                            newImg = newImg.replaceAll("\\s+","");
                            String[] newImgTokens = newImg.split(",");
                          
                            for (String s : newImgTokens) {
                              String prop = s.substring(0,s.indexOf(":"));
                              prop = prop.toLowerCase();
                              String propvalue = s.substring(s.indexOf(":")+1);
                              propvalue = propvalue.toLowerCase();

                              if (attrs[0] == null && prop.equals("gani"))         attrs[0] = propvalue + ".gani";
                              else if (attrs[1] == null && prop.equals("skin"))    attrs[1] = propvalue;
                              else if (attrs[2] == null && prop.equals("coat"))    attrs[2] = propvalue;
                              else if (attrs[3] == null && prop.equals("sleeves")) attrs[3] = propvalue;
                              else if (attrs[4] == null && prop.equals("shoes"))   attrs[4] = propvalue;
                              else if (attrs[5] == null && prop.equals("belt"))    attrs[5] = propvalue;
                              else if (attrs[6] == null && prop.equals("attr1"))   attrs[6] = propvalue;
                              else if (attrs[7] == null && prop.equals("attr2"))   attrs[7] = propvalue;
                              else if (attrs[8] == null && prop.equals("attr3"))   attrs[8] = propvalue;
                              else if (attrs[9] == null && prop.equals("sword"))   attrs[9] = propvalue;
                              else if (attrs[10] == null && prop.equals("shield")) attrs[10] = propvalue;
                              else if (attrs[11] == null && prop.equals("head"))   attrs[11] = propvalue;
                              else if (attrs[12] == null && prop.equals("body"))   attrs[12] = propvalue;
                              else if (attrs[13] == null && prop.equals("param1")) attrs[13] = propvalue;
                              else if (attrs[14] == null && prop.equals("dir"))    attrs[14] = propvalue;
                            }
                            
                            if (attrs[0] == null) attrs[0] = "idle.gani";
                            
                            foundshowcharacter = true;
                          }
                        }
                      }
                    } 
                    
                    if (npc_imgpart.indexOf("setcharprop") > -1) {     
                      String prop = npc_imgpart.substring(12,npc_imgpart.indexOf(",")).toLowerCase();
                      prop = prop.replaceAll("\\s+","");
                      String propvalue = npc_imgpart.substring(npc_imgpart.indexOf(",")+1,npc_imgpart.indexOf(";"));
                      propvalue = propvalue.trim();
                      
                      if (prop.equals("#c0") && attrs[1] == null) attrs[1] = propvalue;
                      else if (prop.equals("#c1") && attrs[2] == null) attrs[2] = propvalue;
                      else if (prop.equals("#c2") && attrs[3] == null) attrs[3] = propvalue;
                      else if (prop.equals("#c3") && attrs[4] == null) attrs[4] = propvalue;
                      else if (prop.equals("#c4") && attrs[5] == null) attrs[5] = propvalue;
                      else if (prop.equals("#p1") && attrs[6] == null) attrs[6] = propvalue;
                      else if (prop.equals("#p2") && attrs[7] == null) attrs[7] = propvalue;
                      else if (prop.equals("#p3") && attrs[8] == null) attrs[8] = propvalue;
                      else if (prop.equals("#1") && attrs[9] == null)  attrs[9] = propvalue;
                      else if (prop.equals("#2") && attrs[10] == null)  attrs[10] = propvalue;
                      else if (prop.equals("#3") && attrs[11] == null)  attrs[11] = propvalue;
                      else if (prop.equals("#8") && attrs[12] == null)  attrs[12] = propvalue;
                    }
                    
                    if (npc_imgpart.startsWith("dir") || npc_imgpart.startsWith("this.dir")) {
                      npc_imgpart = npc_imgpart.replaceAll("\\s+","");
                      npc_imgpart = npc_imgpart.replace("this.","");
                      int findequal = npc_imgpart.indexOf("=")+1;
                      
                      if (npc_imgpart.substring(0,findequal-1).equals("dir") && attrs[14] == null) {
                        String test_dir = npc_imgpart.substring(findequal,findequal+1);
                        try {
                          Integer.parseInt(test_dir);
                          attrs[14] = npc_imgpart.substring(findequal,findequal+1);
                        } catch(Exception e) {
                          writeLog(e);
                        }
                      }
                    } else if (npc_imgpart.trim().startsWith("x") || npc_imgpart.trim().startsWith("this.x")) {
                      // Parse X modifications to NPC
                      npc_imgpart = npc_imgpart.trim();
                      npc_imgpart = npc_imgpart.replace("this.","");
                      npc_imgpart = npc_imgpart.replaceAll("\\s+","");
                      //npc_imgpart = npc_imgpart.replaceAll("\\+","&#43;");
                      npc_imgpart = npc_imgpart.replace(";","");
                      if (npc_imgpart.indexOf("++") > -1) NPCx += 1;
                      else if (npc_imgpart.indexOf("--") > -1) NPCx -= 1;
                      else if (npc_imgpart.indexOf("+=") > -1) {
                        String[] npc_tokens = npc_imgpart.split("\\+=");
                        NPCx += findDouble(npc_tokens[1]);
                      } else if (npc_imgpart.indexOf("-=") > -1) {
                        String[] npc_tokens = npc_imgpart.split("-=");
                        NPCx -= findDouble(npc_tokens[1]);
                      } else if (npc_imgpart.indexOf("=") > -1) {
                        String[] npc_tokens = npc_imgpart.split("=");
                        Double newX = findDouble(npc_tokens[1]);
                        if (newX >= 0) NPCx = findDouble(npc_tokens[1]);
                      }
                    } else if (npc_imgpart.trim().startsWith("y") || npc_imgpart.trim().startsWith("this.y")) {
                      // Parse Y modifications to NPC
                      npc_imgpart = npc_imgpart.trim();
                      npc_imgpart = npc_imgpart.replace("this.","");
                      npc_imgpart = npc_imgpart.replaceAll("\\s+","");
                      //npc_imgpart = npc_imgpart.replaceAll("\\+","&#43;");
                      npc_imgpart = npc_imgpart.replace(";","");
                      if (npc_imgpart.indexOf("++") > -1) NPCy += 1;
                      else if (npc_imgpart.indexOf("--") > -1) NPCy -= 1;
                      else if (npc_imgpart.indexOf("+=") > -1) {
                        String[] npc_tokens = npc_imgpart.split("\\+=");
                        NPCy += findDouble(npc_tokens[1]);
                      } else if (npc_imgpart.indexOf("-=") > -1) {
                        String[] npc_tokens = npc_imgpart.split("-=");
                        NPCy -= findDouble(npc_tokens[1]);
                      } else if (npc_imgpart.indexOf("=") > -1) {
                        String[] npc_tokens = npc_imgpart.split("=");
                        Double newY = findDouble(npc_tokens[1]);
                        if (newY >= 0) NPCy = findDouble(npc_tokens[1]);
                      }
                    } else if (npc_imgpart.startsWith("this.")) {
                      String[] checkFor = {"attr[1]","attr[2]","attr[3]","body","bodyimg","colors[0]","colors[1]","colors[2]","colors[3]","colors[4]","head","headimg","ignorerender","shield","shieldimg","sword","swordimg"};

                      npc_imgpart = npc_imgpart.replaceAll("\"","");
                      String prop = npc_imgpart;
                      
                      boolean foundAttr = false;
                      for (String s : checkFor) {
                        if (npc_imgpart.indexOf(s) > -1) {
                          foundAttr = true;
                          break;
                        }
                      }
                      
                      if (foundAttr == true) {
                        if (npc_imgpart.indexOf("=") >= 0) prop = npc_imgpart.substring(5,npc_imgpart.indexOf("=")).toLowerCase();
                        prop = prop.replaceAll("\\s+","");
                        String propvalue = npc_imgpart.substring(npc_imgpart.indexOf("=")+1,npc_imgpart.indexOf(";"));
                        propvalue = propvalue.trim();
    
                        if (prop.equals("colors[0]") && attrs[1] == null) attrs[1] = propvalue;
                        else if (prop.equals("colors[1]") && attrs[2] == null) attrs[2] = propvalue;
                        else if (prop.equals("colors[2]") && attrs[3] == null) attrs[3] = propvalue;
                        else if (prop.equals("colors[3]") && attrs[4] == null) attrs[4] = propvalue;
                        else if (prop.equals("colors[4]") && attrs[5] == null) attrs[5] = propvalue;
                        else if (prop.equals("attr[1]") && attrs[6] == null) attrs[6] = propvalue;
                        else if (prop.equals("attr[2]") && attrs[7] == null) attrs[7] = propvalue;
                        else if (prop.equals("attr[3]") && attrs[8] == null) attrs[8] = propvalue;
                        else if ((prop.equals("sword")  || prop.equals("swordimg")) && attrs[9] == null) attrs[9] = propvalue;
                        else if ((prop.equals("shield") || prop.equals("shieldimg")) && attrs[10] == null) attrs[10] = propvalue;
                        else if ((prop.equals("head")   || prop.equals("headimg")) && attrs[11] == null) attrs[11] = propvalue;
                        else if ((prop.equals("body")   || prop.equals("bodyimg")) && attrs[12] == null) attrs[12] = propvalue;
                        else if (prop.equals("ignorerender") && propvalue.equals("true")) foundignorerender = true;
                      }
                    }
                    
                    if (npc_imgpart.startsWith("showcharacter")) {
                      // Found a showcharacter?
                      foundshowcharacter = true;
                    } else if (npc_imgpart.startsWith("setcharani") && foundgani == false) {
                        // Found setcharani? Check for 'else' to omit overwriting original setcharani
                        npc_imgpart = npc_imgpart.replace("this.","");
                        npc_imgpart = npc_imgpart.replace("("," ");
                        npc_imgpart = npc_imgpart.replace(")","");
                        npc_imgpart = npc_imgpart.replaceAll("\"","");
                        npc_imgpart = npc_imgpart.trim();
                        
                        String[] tokens = npc_imgpart.split("\\s+");
                        
                        //npc_imgpart = tokens[1].substring(npc_imgpart.indexOf(",")).toLowerCase();
                        String ganiname = tokens[1].substring(0,tokens[1].indexOf(",")).toLowerCase();
                        String ganiparam = tokens[1].substring(tokens[1].indexOf(",")).toLowerCase();
                      
                        if (ganiname.indexOf("[") > -1) ganiname = ganiname.substring(0,ganiname.indexOf("["));
                        attrs[0] = ganiname + ".gani";
                        attrs[13] = ganiparam;
                        
                        foundgani = true;
                    } else if (npc_imgpart.startsWith("setimgpart")) {
                      // If 'setimgpart' is found, append its values to the render data
                      npc_imgpart = npc_imgpart.replaceAll("\"","");
                      npc_imgpart = npc_imgpart.replace("this.","");
                      npc_imgpart = npc_imgpart.replace("("," ");
                      npc_imgpart = npc_imgpart.replace(")","");
                      
                      int startparse = npc_imgpart.indexOf(",");
                      int endparse;
                      if (npc_imgpart.indexOf(")") > -1) endparse = npc_imgpart.indexOf(")");
                      else endparse = npc_imgpart.indexOf(";");
                      String imgname = npc_imgpart.substring(npc_imgpart.indexOf(" ")+1,startparse);
                      String partdata = npc_imgpart.substring(startparse,endparse);
                      partdata = partdata.replaceAll("\\s+","");

                      String imgpartvalues = partdata.replace(","," ");
                      
                      if (!foundignorerender) level_file.add("NPC " + imgname + " " + NPCx + " " + NPCy + imgpartvalues);
                      foundsetimgpart = true;
                      break;
                    } else if (npc_imgpart.startsWith("NPCEND")) {
                      // End of NPC is found, stop the loop
                      // If showcharacter is dound, but no gani is found provide it with default 'idle.gani'
                      if (foundshowcharacter == true && attrs[0] == null) attrs[0] = "idle.gani";
                      // Assign the gani the position of the NPC and add it to the gani StringList
                      if (attrs[0] != null && !foundignorerender) {
                        String[] pos = {String.valueOf(NPCx),String.valueOf(NPCy)};
                        String[][] concat = {attrs,pos};
                        ganis.add(concat);
                      }
                      break;
                    }
                    
                    // If showcharacter is dound, but no gani is found provide it with default 'idle.gani'
                    if (foundshowcharacter == true && attrs[0] == null) attrs[0] = "idle.gani";
                    
                    npc_imgpart = level_reader.readLine();
                  }
                  // If no setimgpart is found, add NPC image and position to the list for rendering
                  if (foundsetimgpart == false && !level_file_line.startsWith("NPC -") && !foundignorerender) level_file.add(level_file_line + " 0 0 -1 -1");
                  level_reader.reset();
                }

                level_file_line = level_reader.readLine();
            }

            level_in.close();

            int[][][] level_tiles = new int[64][64][level_highest_layer + 1];
            for (int level_y = 0; level_y < level_tiles.length; level_y ++) {
                for (int level_x = 0; level_x < level_tiles[level_y].length; level_x ++) {
                    for (int level_layer = 1; level_layer < level_tiles[level_y][level_x].length; level_layer ++) {
                        level_tiles[level_y][level_x][level_layer] = -1;
                    }
                }
            }

            //level_npcs = new ArrayList<String[]>();

            for (String level_line : level_file) {
                String line = level_line;

                if (line.startsWith("BOARD ")) {
                    line = line.substring(6);
                    int tiles_start = Integer.parseInt(line.substring(0, line.indexOf(' ')));

                    line = line.substring(line.indexOf(' ') + 1);
                    int tiles_height = Integer.parseInt(line.substring(0, line.indexOf(' ')));
                    if (tiles_height > 63) {
                        tiles_height = 63;
                    }

                    line = line.substring(line.indexOf(' ') + 1);
                    int tiles_width = Integer.parseInt(line.substring(0, line.indexOf(' ')));
                    if (tiles_width > 64) {
                        tiles_width = 64;
                    }

                    line = line.substring(line.indexOf(' ') + 1);
                    int tiles_layer = Integer.parseInt(line.substring(0, line.indexOf(' ')));

                    line = line.substring(line.indexOf(' ') + 1);
                    for (int level_x = tiles_start; level_x < tiles_width; level_x ++) {
                        level_tiles[tiles_height][level_x][tiles_layer] = getTileNumber(line.substring(level_x * 2, level_x * 2 + 2));
                    }
                } else if (getRenderNPCs() == false) {
                  continue;
                } else if (line.startsWith("NPC ")) {
                  line = line.substring(4);

                  final String REGEX = " ";
                  Pattern p = Pattern.compile(REGEX);
                  String[] partitems = p.split(line);
                 
                  String image_name;
                  
                  int image_x,image_y,image_dx,image_dy,image_dw,image_dh;

                  image_name = partitems[0];
                  image_x     = findInt(partitems[1]);
                  image_y     = findInt(partitems[2]);
                  image_dx    = findInt(partitems[3]);
                  image_dy    = findInt(partitems[4]);
                  image_dw    = findInt(partitems[5]);
                  image_dh    = findInt(partitems[6]);
                  
                  if (image_x < -64 || image_x > 127) {
                    getListener().sendMessage("Warning : The images x of " + image_name + " is " + (image_x < -64 ? "smaller then -64" : "bigger then 127"));
                    continue;
                  }

                  if (image_y < -64 || image_y > 127) {
                    getListener().sendMessage("Warning : The images y of " + image_name + " is " + (image_y < -64 ? "smaller then -64" : "bigger then 127"));
                    continue;
                  }
                  String[] temp_arr = {image_name,Integer.toString(image_x),Integer.toString(image_y),Integer.toString(image_dx),Integer.toString(image_dy),Integer.toString(image_dw),Integer.toString(image_dh),level_name};
                  level_npcs.add(temp_arr);
               }
            }

            // Create an empty buffer for tileset
            BufferedImage finaltileset = new BufferedImage(tileset.getWidth(),tileset.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics g = finaltileset.getGraphics();
            // Draw assigned tileset to tileset buffer
            g.drawImage(tileset, 0, 0, null);
            
            // Loop through tiledefs and draw them over the buffer, but only if the level name starts with the prefix
            // From Dusty: Upon further inspection, adding addtiledef to this buffer will cause issues.
            // It will render the entire tileset over addtiledef2's, depending on the order of which they are parsed
            // I will have to revisit this.
            for (String[] i : tiledefs) {
              if (level_name.startsWith(i[0])) {
                try {
                  BufferedImage tiledef_image = ImageIO.read(new File(getImageLocation(i[1])));
                  /* Was using this to try to force addtiledef's to be opaque
                  BufferedImage temptiledef_image = ImageIO.read(new File(getImageLocation(i[1])));
                  BufferedImage tiledef_image = new BufferedImage(temptiledef_image.getWidth(),temptiledef_image.getHeight(), BufferedImage.OPAQUE);
  
                  Graphics tg = tiledef_image.getGraphics();
                  tg.drawImage(temptiledef_image, 0, 0, null);
                  */
                  
                  if (tiledef_image != null) {
                    g.drawImage(tiledef_image,findInt(i[2]),findInt(i[3]), null);
                  }
                } catch (IOException e) {
                  writeLog(e);
                  getListener().sendMessage("Error: Couldn't load the file " + i[1]);
                }
              }
            }
            
            for (int level_y = 0; level_y < level_tiles.length; level_y ++) {
                for (int level_x = 0; level_x < level_tiles[level_y].length; level_x ++) {
                    for (int level_layer = 0; level_layer < level_tiles[level_y][level_x].length; level_layer ++) {
                        if (level_tiles[level_y][level_x][level_layer] < 0) {
                            continue;
                        }
                        int[] tile_xy = getTileXY(level_tiles[level_y][level_x][level_layer]);
                        tiles_g2d.drawImage(finaltileset, level_x * intTile, level_y * intTile, level_x * intTile + intTile, level_y * intTile + intTile, tile_xy[0], tile_xy[1], tile_xy[0] + 16, tile_xy[1] + 16, null);
                    }
                }
            }
            
            // Loop through the found ganis and render
            if (getRenderNPCs() == true) {
              for (String[][] gani: ganis) {
                if (gani[0][12] != null && getRenderChars() == false) continue;
                BufferedImage gani_render = getGani(gani[0]);
                if (gani_render == null) {
                  getListener().sendMessage("Warning : Couldn't render the gani " + gani[0][0]);
                  continue;
                }
                 //System.out.println(gani[0][0] + " : " + gani[1][0] + "," + gani[1][1]);
                int NPCx = (int)(Double.parseDouble(gani[1][0]) * intTile + (int)(ganiOffsetx*getScale()));
                int NPCy = (int)(Double.parseDouble(gani[1][1]) * intTile + (int)(ganiOffsety*getScale()));
                int NPCw = (int)(gani_render.getWidth()*getScale());
                int NPCh = (int)(gani_render.getHeight()*getScale());
              
                //System.out.println("Rendering: " + gani[0][0] + " : " + ganiOffsetx + " : " + ganiOffsety);
                npcs_g2d.drawImage(gani_render,NPCx,NPCy,NPCw,NPCh,null);
              }

              // If not rendering a gmap, or if rendering a gmap that will be split
              // render NPCs. Otherwise NPCs will be rendered in GMAP function(to avoid clipping)
              if (renderinggmap == false || getSplit() == true) {
                for (String[] npc : level_npcs) {
                  // omit lights and tileset images
                  if (npc[0] == null || npc[0].toLowerCase() == "pics1.png" || npc[0].toLowerCase().contains("light")) continue;
                  int image_x  = findInt(npc[1]);
                  int image_y  = findInt(npc[2]);
                  int image_dx = findInt(npc[3]);
                  int image_dy = findInt(npc[4]);
                  int image_dw = findInt(npc[5]);
                  int image_dh = findInt(npc[6]);
              
                  try {
                    BufferedImage npc_image = ImageIO.read(new File(getImageLocation(npc[0])));
                    if (npc_image == null) {
                        getListener().sendMessage("Warning : Unknown image type " + npc[0].substring(npc[0].lastIndexOf(".") + 1).toUpperCase());
                    } else {
                        image_dw = image_dw == -1 ? npc_image.getWidth() : image_dw;
                        image_dh = image_dh == -1 ? npc_image.getHeight() : image_dh;
                    
                        int render_x = (image_x) * intTile;
                        int render_y = (image_y) * intTile;
                        int render_w = (int)(image_dw * getScale());
                        int render_h = (int)(image_dh * getScale());

                        npcs_g2d.drawImage(npc_image,render_x,render_y,render_x + render_w,render_y + render_h,
                                           image_dx,image_dy,image_dx+image_dw,image_dy+image_dh,null);
                     }
                  } catch (IOException e) {
                    writeLog(e);
                    getListener().sendMessage("Warning : Couldn't find the image " + npc[0]);
                  }
                }
              }
            }

            // If not rendering a gmap, let the user know the image is being saved
            if (renderinggmap == false) getListener().sendMessage("Rendering and saving the image...");

            npcs_g2d.dispose();
            if (getRenderNPCs() == true) tiles_g2d.drawImage(gmap_npcs, 0, 0, null);
            tiles_g2d.dispose();
            
            // return the image to either be saved or stitched into the gmap render.
            if (gmap_tiles != null) return gmap_tiles;

        } catch (IOException e) {
          // Let the user know if the level could not be found
          writeLog(e);
          getListener().sendMessage("Error: Couldn't load the file " + sourcePath.substring(sourcePath.lastIndexOf(File.separator) + 1));
        } catch (OutOfMemoryError e) {
          renderinggmap = false;
          writeLog(e);
          getListener().sendMessage("Error: Out of memory! Try MoreMemory.bat");
          getListener().doneGenerating();
        }
        //getListener().sendMessage("Error: Level was unable to be rendered for an unknown reason.");
        return null;
    }

    private BufferedImage renderGmap(File source) {
      int intTile = (int) (16 * getScale());
      int intDimension = (int) (intTile*64);
      
      renderinggmap = true;
      String sourcePath = source.getAbsolutePath();

      try {
        FileReader gmap_in;
        gmap_in = new FileReader(source);
        BufferedReader gmap_reader = new BufferedReader(gmap_in);

        int gmap_width = 0,gmap_height = 0;

        boolean parselevels = false;
        
        String[] levels = new String[0];
        
        String gmap_line = gmap_reader.readLine();
        int gmap_yrender = 0;
        while (gmap_line != null) {
          if (gmap_width < 0 || gmap_height < 0) break;
          
          if (gmap_line.startsWith("LEVELNAMESEND")) {
            parselevels = false;
            gmap_line = gmap_reader.readLine();
            continue;
          } 
 
          if (gmap_line.startsWith("WIDTH ")) {
            gmap_width = Integer.parseInt(gmap_line.substring(6));
            levels = new String[gmap_width*gmap_height];
          } else if (gmap_line.startsWith("HEIGHT ")) {
            gmap_height = Integer.parseInt(gmap_line.substring(7));
            levels = new String[gmap_width*gmap_height];
          } else if (gmap_line.startsWith("LEVELNAMES")) {
            parselevels = true;
          } else if (parselevels == true) {
            String[] level_tokens = gmap_line.split(",");
           
            
            if (level_tokens.length > gmap_width) {
              getListener().sendMessage("Error: GMAP format is incorrent!");
              return null;
            }
            
            for (int i=0;i<level_tokens.length;i++) {
              String levelname = level_tokens[i].replaceAll("\"","");
              levelname = levelname.trim();
              if (i + gmap_yrender*gmap_width < levels.length) levels[i + gmap_yrender*gmap_width] = levelname;
              else {
                System.out.println(i + "," + gmap_yrender + "," + gmap_width + " : " + levels.length);
                getListener().sendMessage("Error: Trouble parsing GMAP level data!");
              }
            }
            
            gmap_yrender++;
          }
          gmap_line = gmap_reader.readLine();
        }
        
        int gmap_image_width = (int) (gmap_width * intDimension);
        int gmap_image_height = (int) (gmap_height * intDimension);
        
        BufferedImage gmapImage = new BufferedImage(gmap_image_width,gmap_image_height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = gmapImage.getGraphics();
        
        g.setColor(new Color(0));
        g.fillRect(0,0,gmapImage.getWidth(),gmapImage.getHeight());
        
        for (int i = 0;i < levels.length;i++) {
          File nwFile = new File(sourcePath.substring(0, sourcePath.lastIndexOf(File.separator) + 1) + levels[i]);
    
          BufferedImage nw_render = renderLevel(nwFile);
          
          if (nw_render == null) continue;
          
          getListener().sendMessage("Rendering level: " + nwFile.getName());
         
          int draw_x = (i%gmap_width) * nw_render.getWidth();
          int draw_y = (int)(i/gmap_width) * nw_render.getHeight();
          
          if (getSplit() == true) {
            try {
              String partsDir = getOutputFile().getAbsolutePath();
              partsDir = partsDir.substring(0,partsDir.lastIndexOf(".")) + File.separator;
              String nwName = nwFile.getName().substring(0,nwFile.getName().length()-3);
              
              File file = new File(partsDir + nwName + ".png");
              file.getParentFile().mkdir();
              ImageIO.write(nw_render, "png", file);
            } catch (IOException e) {
              writeLog(e);
              getListener().sendMessage("Error: Couldn't save the image " + nwFile.getName().substring(0,nwFile.getName().length()-3) + ".png");
            }
          } else g.drawImage(nw_render,draw_x,draw_y,null);
          //g.drawImage(nw_render,draw_x,draw_y,draw_x + nw_render.getWidth(),draw_y + nw_render.getHeight(),null);
        }
        
        if (getSplit() == true) return null;
        
        if (getRenderNPCs() == true) {
          for (int i = 0;i < levels.length;i++) {
            int draw_x = (i%gmap_width) * intDimension;
            int draw_y = (int)(i/gmap_width) * intDimension;
            
            for (String[] npc : level_npcs) {
              if (!npc[7].equals(levels[i])) continue;
              if (npc[0] == null || npc[0].toLowerCase() == "pics1.png" || npc[0].toLowerCase().contains("light")) continue;
              int image_x  = findInt(npc[1]);
              int image_y  = findInt(npc[2]);
              int image_dx = findInt(npc[3]);
              int image_dy = findInt(npc[4]);
              int image_dw = findInt(npc[5]);
              int image_dh = findInt(npc[6]);
          
              try {
                npc[0] = npc[0].toLowerCase();
                if (npc[0].endsWith(".mng")) {
                  getListener().sendMessage("Warning : MNG images not supported @ " + npc[0]);
                  continue;
                }
                if (npc[0] == null || npc[0].equals("-")) continue;
                
                BufferedImage npc_image = ImageIO.read(new File(getImageLocation(npc[0])));
                if (npc_image == null) {
                    getListener().sendMessage("Warning : Unknown image type " + npc[0].substring(npc[0].lastIndexOf(".") + 1).toUpperCase());
                    continue;
                } else {
                    image_dw = image_dw == -1 ? npc_image.getWidth() : image_dw;
                    image_dh = image_dh == -1 ? npc_image.getHeight() : image_dh;
                
                    int render_x = draw_x + (image_x) * intTile;
                    int render_y = draw_y + (image_y) * intTile;
                    int render_w = (int)(image_dw * getScale());
                    int render_h = (int)(image_dh * getScale());

                    g.drawImage(npc_image,render_x,render_y,render_x + render_w,render_y + render_h,
                                       image_dx,image_dy,image_dx+image_dw,image_dy+image_dh,null);
                 }
              } catch (IOException e) {
                writeLog(e);
                getListener().sendMessage("Warning : Couldn't find the image " + npc[0]);
              }
            }
          }
        }
        
        if (gmapImage != null) return gmapImage;
        
      } catch (FileNotFoundException e) {
        writeLog(e);
      } catch (IOException e) {
        writeLog(e);
      }
      getListener().sendMessage("Error: GMAP was unable to be rendered for an unknown reason.");
      return null;
    }

    private int getTileNumber(String tile_string) {
        String base64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        return base64.indexOf(tile_string.substring(0, 1)) * 64 + base64.indexOf(tile_string.substring(1, 2));
    }

    private int[] getTileXY(int tile_number) {
        int[] tile_xy = {(tile_number % 16 + tile_number / 512 * 16) * 16, (tile_number / 16 % 32) * 16};
        return tile_xy;
    }
    
    private void CheckFILENAMECACHE() {
      filenamecacheDir = getGraalDir();
      if (!filenamecacheDir.endsWith("\\")) filenamecacheDir = filenamecacheDir + "\\";

      File filecheck = new File(filenamecacheDir + "FILENAMECACHE.txt"),filecheck2;
      if (filecheck.exists() == false) {
        getListener().sendMessage("Error: Failed to find FILENAMECACHE.txt in " + filenamecacheDir + ", falling back on C:\\Program Files\\Graal\\...");
        filenamecacheDir = "C:\\Program Files\\Graal\\";
        filecheck2 = new File(filenamecacheDir + "FILENAMECACHE.txt");
        if (filecheck2.exists() == false) getListener().sendMessage("Error: Failed to find FILENAMECACHE.txt on fallback search.");
      } else {
        
      }
    }

    private String getImageLocation(String imageName) {
      String GraalFolder = filenamecacheDir;
      
      if (imageName.equals("-")) return null;
      if (imageName.toLowerCase().endsWith(".mng")) {
        getListener().sendMessage("Warning: Does not support .MNG for " + imageName);
        return null;
      }
      
        try {
          //FileReader filenamecache = new FileReader(getGraalDir().substring(0, getGraalDir().lastIndexOf(File.separator) + 1) + "FILENAMECACHE.txt");
          
          FileReader filenamecache = new FileReader(GraalFolder + "FILENAMECACHE.txt");
     
          BufferedReader cache_scan = new BufferedReader(filenamecache);
          String cache_scan_line = cache_scan.readLine();
          
          while (cache_scan_line != null) {
            if (cache_scan_line.indexOf("\\" + imageName) > -1 || cache_scan_line.startsWith(imageName)) {
              return GraalFolder + cache_scan_line.substring(0,cache_scan_line.indexOf(","));
            }
            cache_scan_line = cache_scan.readLine();
          }
          
        } catch (FileNotFoundException e) {
          writeLog(e);
          //getListener().sendMessage("Error: Failed to find " + imageName);
        } catch (IOException e) {
          writeLog(e);
          //getListener().sendMessage("Error: Failed to load " + imageName);
        }
        return GraalFolder;
    }
    
    private BufferedImage getGani(String[] attr) {
      // attrs = {gani,#c0,#c1,#c2,#c3,#c4,#P1,#P2,#P3,#1,#2,#3,#8,param1,dir};
      //if (tiledef_image != null) {
      //  g.drawImage(tiledef_image,Integer.parseInt(i[2]),Integer.parseInt(i[3]), null);

      String ganiName = attr[0];
      if (ganiName == null) return null;
      int dir = 2;
      if (attr[14] != null) {
        dir = Integer.parseInt(attr[14]);
      }

      ganiOffsetx = ganiOffsety = 0;
      
      // DEFINE DEFAULTS
      String[] defaultcolors = {"white" ,"yellow","orange","pink"  ,"red"   ,"darkred","lightgreen","green" ,"darkgreen","lightblue","blue"  ,"darkblue","brown" ,"cynober","purple","darkpurple","lightgray","gray"  ,"black" };               
      int[] defaultcolorshex = {0xffffff,0xffff00,0xffad6b,0xff8484,0xff0000,0xce1829 ,0x84ff84    ,0x00ff00,0x00c600   ,0x8484ff   ,0x0000ff,0x0000c6  ,0x840000,0x00ffff ,0xff00ff,0x840084    ,0xcecece   ,0x848484,0x000008};
      
      String img_sprites   = "sprites.png";
      String img_attr1     = null;
      String img_attr2     = null;
      String img_attr3     = null;
      String img_sword     = "sword1.png";
      String img_shield    = "shield1.png";
      String img_head      = "head19.png";
      String img_body      = "body.png";
      String img_param1    = null;
      
      String color_skin    = "orange";
      String color_coat    = "white";
      String color_sleeves = "red";
      String color_shoes   = "blue";
      String color_belt    = "black";
      
      String[][] sprites;
      ArrayList<int[]> rendersprites = new ArrayList<int[]>();
      
      FileReader gani_in;
      
      try {
        gani_in = new FileReader(getImageLocation(ganiName));
        BufferedReader gani_reader = new BufferedReader(gani_in);
        
        gani_reader.mark(20480);
        
        String gani_line = gani_reader.readLine();
        String sprite_scan = gani_reader.readLine();
        
        int maxsprite = 0;
        for (int i = 0;i<300;i++) {
          parsingLine = sprite_scan;
          if (sprite_scan == null || !sprite_scan.startsWith("SPRITE")) break;
          sprite_scan = sprite_scan.replaceAll("\\s+", " ");
          String[] parse = sprite_scan.split("\\s");
          maxsprite = Integer.parseInt(parse[1]);
          
          sprite_scan = gani_reader.readLine();
        }
        
        sprites = new String[maxsprite+1][5];

        gani_reader.reset();
        
        boolean foundframe = false;
        
        while (gani_line != null) {
          parsingLine = gani_line;
          gani_line = gani_line.replaceAll("\\s+", " ");
          if (gani_line.startsWith("SPRITE")) {
            String[] parse = gani_line.split("\\s");
            
            int spritenumber = Integer.parseInt(parse[1]);
            
            if (spritenumber < 0 || spritenumber > sprites.length-1) {
              gani_line = gani_reader.readLine();
              continue;
            }
          
            String[] temparr = {parse[2],parse[3],parse[4],parse[5],parse[6]};
            
            sprites[spritenumber] = temparr;
          } else if (gani_line.startsWith("SINGLEDIRECTION")) {
            dir = 0;
          } else if (gani_line.startsWith("ANI")) {
            for (int i = 0;i<dir;i++) {
              gani_line = gani_reader.readLine();
            }
            foundframe = true;
          } else if (foundframe == true) {
            gani_line = gani_line.replaceAll("\\s+"," ");
            gani_line = gani_line.replaceAll(",\\s+",",");
            gani_line = gani_line.trim();
            
            String[] tokensprites = gani_line.split(",");
            for (String i : tokensprites) {
              String[] tokenspritedata = i.split(" ");
              if (Integer.parseInt(tokenspritedata[0]) < 0) continue;
              int[] temparr = {Integer.parseInt(tokenspritedata[0]),
                               Integer.parseInt(tokenspritedata[1]),
                               Integer.parseInt(tokenspritedata[2])
              };
              rendersprites.add(temparr);
            }
            
            break;
          }
          gani_line = gani_reader.readLine();
        }
        
        gani_reader.close();
        
        // attrs = {gani,#c0,#c1,#c2,#c3,#c4,#P1,#P2,#P3,#1,#2,#3,#8,param1,dir};
        if (attr[6]  != null) img_attr1    = attr[6];
        if (attr[7]  != null) img_attr2    = attr[7];
        if (attr[8]  != null) img_attr3    = attr[8];
        if (attr[9]  != null) img_sword    = attr[9];
        if (attr[10] != null) img_shield   = attr[10];
        if (attr[11] != null) img_head     = attr[11];
        if (attr[12] != null) img_body     = attr[12];
        if (attr[13] != null) img_param1   = attr[13];
        
        if (attr[1] != null) color_skin    = attr[1];
        if (attr[2] != null) color_coat    = attr[2];
        if (attr[3] != null) color_sleeves = attr[3];
        if (attr[4] != null) color_shoes   = attr[4];
        if (attr[5] != null) color_belt    = attr[5];
        
        // Find largest sprite to create appropriate image buffer
        int sprite_minX = 0;
        int sprite_minY = 0;
        int sprite_maxW = 1;
        int sprite_maxH = 1;
        
        for (int i[] : rendersprites) {
          int sprite_X = i[1];
          int sprite_Y = i[2];
          
          if (sprite_X < sprite_minX) sprite_minX = sprite_X;
          if (sprite_Y < sprite_minY) sprite_minY = sprite_Y;
        }
        
        if (sprite_minX < 0) ganiOffsetx = sprite_minX;
        if (sprite_minY < 0) ganiOffsety = sprite_minY;

        for (int i[] : rendersprites) {
          int sprite_X = i[1];
          int sprite_Y = i[2];
          
          int sprite_W = Integer.parseInt(sprites[i[0]][3]) + Math.abs(sprite_minX) + sprite_X;
          int sprite_H = Integer.parseInt(sprites[i[0]][4]) + Math.abs(sprite_minY) + sprite_Y;
          
          if (sprite_W > sprite_maxW) sprite_maxW = sprite_W;
          if (sprite_H > sprite_maxH) sprite_maxH = sprite_H;
        }
        
        if (sprite_maxW < 48) sprite_maxW = 48;
        if (sprite_maxH < 48) sprite_maxH = 48;
        
        BufferedImage ganiImage = new BufferedImage(sprite_maxW,sprite_maxH, BufferedImage.TYPE_INT_ARGB);
        Graphics g = ganiImage.getGraphics();
        
        boolean isbody = false;
        for (int i[] : rendersprites) {
          isbody = false;
          String sprite_img   = sprites[i[0]][0];
          int    sprite_drawx = i[1] - ganiOffsetx;
          int    sprite_drawy = i[2] - ganiOffsety;
          if (sprite_img.equals("SPRITES")) sprite_img = img_sprites;
          else if (sprite_img.equals("BODY")) {
            isbody = true;
            sprite_img = img_body;
          } else if (sprite_img.equals("HEAD")) sprite_img = img_head;
          else if (sprite_img.equals("SWORD")) sprite_img = img_sword;
          else if (sprite_img.equals("SHIELD")) sprite_img = img_shield;
          else if (sprite_img.equals("ATTR1")) sprite_img = img_attr1;
          else if (sprite_img.equals("ATTR2")) sprite_img = img_attr2;
          else if (sprite_img.equals("ATTR3")) sprite_img = img_attr3;
          else if (sprite_img.equals("PARAM1")) sprite_img = img_param1;
          
          //System.out.println(getImageLocation(sprite_img));
          
          try {
            parsingLine = "Rendering image: " + sprite_img;
            if (sprite_img == null) continue;
            BufferedImage sprite_render = ImageIO.read(new File(getImageLocation(sprite_img)));
            if (sprite_render == null) continue;
            
            
            int sprite_sx = Integer.parseInt(sprites[i[0]][1]);
            int sprite_sy = Integer.parseInt(sprites[i[0]][2]);
            int sprite_sw = Integer.parseInt(sprites[i[0]][3]);
            int sprite_sh = Integer.parseInt(sprites[i[0]][4]);
            
            if (isbody) {
              //transparent = 008400;
              //skin = ffad6b;
              //coat = ffffff;
              //belt = 0000ff;
              //sleeve = ff0000;
              //shoes = ce1829;
              int bodycolors[][] = {
                  {0x008400,0xffad6b,0xffffff,0x0000ff,0xff0000,0xce1829},
                  {0x300000,0x400000,0x500000,0x600000,0x700000,0x800000}
              };
              
              //if (attr[1] != null) color_skin    = attr[1];
              //if (attr[2] != null) color_coat    = attr[2];
              //if (attr[3] != null) color_sleeves = attr[3];
              //if (attr[4] != null) color_shoes   = attr[4];
              //if (attr[5] != null) color_belt    = attr[5];
              
              for (int j=0;j<6;j++) {
                sprite_render = TransformColorToNewColor(sprite_render,new Color(bodycolors[0][j]),new Color(bodycolors[1][j]));
              }

              int newColor = 0;
              //sprite_render = TransformColorToNewColor(sprite_render,new Color(bodycolors[1][0]),new Color(0,0,0,0));
              newColor = defaultcolorshex[findColornameValue(defaultcolors,color_skin)];
              sprite_render = TransformColorToNewColor(sprite_render,new Color(bodycolors[1][1]),new Color(newColor));
              newColor = defaultcolorshex[findColornameValue(defaultcolors,color_coat)];
              sprite_render = TransformColorToNewColor(sprite_render,new Color(bodycolors[1][2]),new Color(newColor));
              newColor = defaultcolorshex[findColornameValue(defaultcolors,color_belt)];
              sprite_render = TransformColorToNewColor(sprite_render,new Color(bodycolors[1][3]),new Color(newColor));
              newColor = defaultcolorshex[findColornameValue(defaultcolors,color_sleeves)];
              sprite_render = TransformColorToNewColor(sprite_render,new Color(bodycolors[1][4]),new Color(newColor));
              newColor = defaultcolorshex[findColornameValue(defaultcolors,color_shoes)];
              sprite_render = TransformColorToNewColor(sprite_render,new Color(bodycolors[1][5]),new Color(newColor));
              /*
              int[] bodypixels = new int[sprite_render.getWidth()*sprite_render.getHeight()];
              sprite_render.getRGB(0,0,sprite_render.getWidth(),sprite_render.getHeight(),
                  bodypixels,0,sprite_render.getWidth());
                  */
            }
            
            //g.setColor(new Color(0,0,255,50));
            //g.fillRect(0,0,ganiImage.getWidth(),ganiImage.getHeight());
            
            g.drawImage(sprite_render,
                        sprite_drawx,sprite_drawy,
                        sprite_drawx+sprite_sw,sprite_drawy+sprite_sh,
                        sprite_sx,sprite_sy,sprite_sx + sprite_sw, sprite_sy + sprite_sh,
                        null);
            
            //System.out.println(sprite_img + " @ " + sprite_drawx + " : " + sprite_drawy + " | " + sprite_maxW + " : " + sprite_maxH);
            
          } catch (FileNotFoundException e) {
            writeLog(e);
            getListener().sendMessage("Error: Can't find image: " + sprite_img);
          } catch (IOException e) {
            writeLog(e);
            getListener().sendMessage("Error: Can't find image: " + sprite_img);
          }
          
        }
        return ganiImage;
        
      } catch (FileNotFoundException e) {
        writeLog(e);
      } catch (IOException e) {
        writeLog(e);
      }
      return null;
    }
    
    private int findColornameValue(String[] a,String s) {
      for (int i=0;i<a.length;i++) {
        if (a[i].equals(s)) {
          return i;
        }
      }
      return 0;
    }
    
    private int findInt(String s) {
      try {
        int return_val = Integer.parseInt(s);
        return return_val;
      } catch (java.lang.NumberFormatException e) {
        writeLog(e);
        try {
          ScriptEngineManager mgr = new ScriptEngineManager();
          ScriptEngine engine = mgr.getEngineByName("JavaScript");
          double math_val = (Double) engine.eval(s);
          int return_val = (int)math_val;
          return return_val;
        } catch (ScriptException e1) {
          writeLog(e);
          getListener().sendMessage("Warning: Could not parse: " + s);
          return -1;
        }
      }
    }
    
    private double findDouble(String s) {
      try {
        double return_val = Double.parseDouble(s);
        return return_val;
      } catch (java.lang.NumberFormatException e) {
        writeLog(e);
        try {
          ScriptEngineManager mgr = new ScriptEngineManager();
          ScriptEngine engine = mgr.getEngineByName("JavaScript");
          double math_val = (Double) engine.eval(s);
          return math_val;
        } catch (ScriptException e1) {
          writeLog(e);
          getListener().sendMessage("Warning: Could not parse: " + s);
          return -1;
        }
      }
    }
    
    // NOT MY(Dusty) WORK!
    private BufferedImage TransformColorToNewColor(BufferedImage image, Color c1, Color c2) {
      // Primitive test, just an example
      final int r1 = c1.getRed();
      final int g1 = c1.getGreen();
      final int b1 = c1.getBlue();
      final int r2 = c2.getRed();
      final int g2 = c2.getGreen();
      final int b2 = c2.getBlue();
      final int a2 = c2.getAlpha();
      ImageFilter filter = new RGBImageFilter() {
        public final int filterRGB(int x, int y, int argb) {
          int a = 255;
          if (a2 > 0) a = (argb & 0xFF000000) >> 24;
          else a = 0;
          int r = (argb & 0xFF0000) >> 16;
          int g = (argb & 0xFF00) >> 8;
          int b = (argb & 0xFF);

          // Check if this color matches c1.  If not, it is not our target color.
          // Don't bother with it in this case.
          if (r != r1 || g != g1 || b != b1)
            return argb;

          // Set r, g, and b to our new color.  Bit-shift everything left to get it
          // ready for re-packing.
          if (a2 > 0) a = a << 24;
          r = r2 << 16;
          g = g2 << 8;
          b = b2;

          // Re-pack our colors together with a bitwise OR.
          //return a | r | g | b;
          return a | r | g | b;
        }
      };

      ImageProducer ip = new FilteredImageSource(image.getSource(), filter);
      //BufferedImage test = new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
      
      //BufferedImage new_renderbuffer = new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
      //Graphics g = ganiImage.getGraphics();
      
      Image new_renderimage = Toolkit.getDefaultToolkit().createImage(ip);
      
      BufferedImage new_renderbuffer = toBufferedImage(new_renderimage);
      
      /*
      try {
        if (ImageIO.createImageInputStream(Toolkit.getDefaultToolkit().createImage(ip)) == null) System.out.println("Test!");
        new_render = ImageIO.read(ImageIO.createImageInputStream(Toolkit.getDefaultToolkit().createImage(ip)));
      } catch (IOException e) {
        e.printStackTrace();
      }
      */
      
      if (new_renderbuffer == null) return null;
      
      return new_renderbuffer;
      //return Toolkit.getDefaultToolkit().createImage(ip);
    }
    
    public static BufferedImage toBufferedImage(Image image) {
      if (image instanceof BufferedImage) {
          return (BufferedImage)image;
      }

      // This code ensures that all the pixels in the image are loaded
      image = new ImageIcon(image).getImage();

      // Determine if the image has transparent pixels; for this method's
      // implementation, see Determining If an Image Has Transparent Pixels
      boolean hasAlpha = true;

      // Create a buffered image with a format that's compatible with the screen
      BufferedImage bimage = null;
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      try {
          // Determine the type of transparency of the new buffered image
          int transparency = Transparency.OPAQUE;
          if (hasAlpha) {
              transparency = Transparency.BITMASK;
          }

          // Create the buffered image
          GraphicsDevice gs = ge.getDefaultScreenDevice();
          GraphicsConfiguration gc = gs.getDefaultConfiguration();
          bimage = gc.createCompatibleImage(
              image.getWidth(null), image.getHeight(null), transparency);
      } catch (HeadlessException e) {
          // The system does not have a screen
      }

      if (bimage == null) {
          // Create a buffered image using the default color model
          int type = BufferedImage.TYPE_INT_RGB;
          if (hasAlpha) {
              type = BufferedImage.TYPE_INT_ARGB;
          }
          bimage = new BufferedImage(image.getWidth(null), image.getHeight(null), type);
      }

      // Copy image to buffered image
      Graphics g = bimage.createGraphics();

      // Paint the image onto the buffered image
      g.drawImage(image, 0, 0, null);
      g.dispose();

      return bimage;
  }
    
    public String getJoin(String s) {
      FileReader gani_in;
      
      try {
        gani_in = new FileReader("ClassImages.txt");
        BufferedReader join_reader = new BufferedReader(gani_in);
        
        String join_line = join_reader.readLine();
        while (join_line != null) {
          if (join_line.startsWith("//")) {
            join_line = join_reader.readLine();
            continue;
          }
          String[] tokens = join_line.split("=");
          if (tokens[0].toLowerCase().equals(s.toLowerCase())) return tokens[1].toLowerCase();
          join_line = join_reader.readLine();
        }
        join_reader.close();
        return null;
      } catch (FileNotFoundException e) {
        writeLog(e);
      } catch (IOException e) {
        writeLog(e);
      }
      
      //ClassImages.txt
      return null;
    }
    
    public void writeLog(String s) {
      try {
        FileWriter fstream = new FileWriter("errorLog.txt",true);
        BufferedWriter out = new BufferedWriter(fstream);
        out.write(s + "\n");
        out.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    
    public void writeLog(Throwable t) {
      try {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        t.printStackTrace(pw);
        pw.flush();
        sw.flush();
        
        FileWriter fstream = new FileWriter("errorLog.txt",true);
        BufferedWriter out = new BufferedWriter(fstream);
        out.write("Print Stack Trace for line: " + parsingLine + "\n");
        out.write(sw.toString() + "\n");
        out.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    
    /*
    public void writeLog(Exception err) {
      try {
        FileWriter fstream = new FileWriter("errorLog.txt",true);
        BufferedWriter out = new BufferedWriter(fstream);
        out.write(err.toString() + "\n");
        out.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    */
}
