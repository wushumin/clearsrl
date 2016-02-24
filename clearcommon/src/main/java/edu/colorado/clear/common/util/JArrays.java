package edu.colorado.clear.common.util;

import java.util.*;

public class JArrays
{
    static public int max(int[] arr)
    {
        int max = Integer.MIN_VALUE;
        for (int i : arr)   max = Math.max(max, i);
        
        return max;
    }
    
    static public int max(ArrayList<Integer> arrlist)
    {
        int max = Integer.MIN_VALUE;
        for (int i : arrlist)   max = Math.max(max, i);
        
        return max;
    }
    
    static public double max(double[] arr)
    {
        double max = Double.MIN_VALUE;
        for (double x : arr)    max = Math.max(max, x);
        
        return max;
    }
    
    static public double min(double[] arr)
    {
        double max = Double.MAX_VALUE;
        for (double x : arr)    max = Math.max(max, x);
        
        return max;
    }
    
    static public int[] toIntArray(StringTokenizer tok)
    {
        int[] arr = new int[tok.countTokens()];
        
        for (int i=0; i<arr.length; i++)
            arr[i] = Integer.parseInt(tok.nextToken());
        
        return arr;
    }
    
    static public double[] toDoubleArray(StringTokenizer tok)
    {
        double[] arr = new double[tok.countTokens()];
        
        for (int i=0; i<arr.length; i++)
            arr[i] = Double.parseDouble(tok.nextToken());
        
        return arr;
    }
    
    static public String[] toStringArray(StringTokenizer tok)
    {
        String[] arr = new String[tok.countTokens()];
        
        for (int i=0; i<arr.length; i++)
            arr[i] = tok.nextToken();
        
        return arr;
    }

    static public ArrayList<Integer> toArrayList(StringTokenizer tok)
    {
        ArrayList<Integer> arrlist = new ArrayList<Integer>(tok.countTokens());
        
        while (tok.hasMoreTokens())
            arrlist.add(Integer.parseInt(tok.nextToken()));
        
        return arrlist;
    }
    
    static public ArrayList<Integer> toArrayList(int[] arr)
    {
        ArrayList<Integer> arrlist = new ArrayList<Integer>(arr.length);
        
        for (int value : arr)   arrlist.add(value);
        return arrlist;
    }
    
    static public String toString(ArrayList<Integer> arr, String delim)
    {
        String str = "";
        for (int i : arr)   str += i + delim;
        
        return str.trim();
    }
    
    static public String toString(int[] arr, String delim)
    {
        String str = "";
        for (int i : arr)   str += i + delim;
        
        return str.trim();
    }
    
    // copy d1 = d2 
    static public void copy(double[] d1, double[] d2)
    {
        for (int i=0; i < d1.length && i < d2.length; i++)
            d1[i] = d2[i];
    }
}
