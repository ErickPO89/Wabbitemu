package com.Revsoft.Wabbitemu.utils;

public class KeyMapping {
	int key;
	int group;
	int bit;
	
	public KeyMapping(int key, int group, int bit) {
		this.key = key;
		this.group = group;
		this.bit = bit;
	}
	
	public int getKey() {
		return key;
	}
	
	public int getGroup() {
		return group;
	}
	
	public int getBit() {
		return bit;
	}
}