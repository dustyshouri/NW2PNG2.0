package src.born2kill.nw2png;

import java.awt.image.BufferedImage;

public class RenderObj {
  BufferedImage img;
  int[] data;
  int layer;
  String level;
  
  public RenderObj(BufferedImage img,int[] data,String level,int layer) {
    this.img = img;
    this.data = data;
    this.layer = layer;
    this.level = level;
  }
  
  public BufferedImage getImg() {
    return img;
  }
  
  public int[] getData() {
    return data;
  }
  
  public int getLayer() {
    return layer;
  }
  
  public String getLevel() {
    return level;
  }
}
