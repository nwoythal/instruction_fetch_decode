package instruction_fetch_decode;
import java.io.*;
import java.util.ArrayList;

public class Instruction_Fetch_Decode 
{
	public static int program_counter=0x00400000; //Apparently hex works for integer declarations, which is cool
	public static ArrayList<String> operations=new ArrayList<String>();
	public static String[] register_list= //Global so there's no initialization on every get_registers call
		{
			"$0", "$at", "$v0", "$v1", "$a0", "$a1", 
			"$a2", "$a3", "$t0", "$t1", "$t2", "$t3", 
			"$t4", "$t5", "$t6", "$t7", "$s0", "$s1", 
			"$s2", "$s3", "$s4", "$s5", "$s6", "$s7",
			"$t8", "$t9", "$k0", "$k1", "$gp", "$sp",
			"$fp", "$ra"
	   };
	public static void main(String[] args)
	{
		try
		{
			Load("HW2MachineCode.txt");
			for(int i=0;i<operations.size();i++)
			{
				System.out.println(decode(fetch())); //Pull machine code instruction from list, then translate into MIPS language
				program_counter+=4;
			}
		}
		catch(IOException | InvalidOpException ex)
		{
			System.out.println("Exception thrown: "+ex);
		}
	}

	//Translate the hexadecimal to valid MIPS (if possible, otherwise an error is thrown)
	public static String decode(String hex) throws InvalidOpException
	{
		String instruction="";
		try
		{
			instruction="0x"+String.format("%8s", Integer.toHexString(program_counter)).replace(' ', '0')+"\t";
			hex=hex.substring(2);
			int i=Integer.parseInt(hex, 16);
			String binary=String.format("%32s", Integer.toBinaryString(i)).replace(' ', '0'); //Ensure when it is translated to binary, we don't lose precision
			String binary_op=binary.substring(0, 6);
			switch(Integer.parseInt(binary_op, 2)) //Op code lookup table
			{
				//R-Types
				case 0:
					try
					{
						return instruction+get_function(binary.substring(6));
					}
					catch(InvalidOpException ex)
					{
						System.out.println("Exception thrown: "+ex);
					}
					break;
				//J-Types
				case 2:
					instruction=instruction+"j\t";
					instruction=instruction+get_jump_loc(binary.substring(6));
					return instruction;
				case 3:
					instruction=instruction+"jal\t";
					instruction=instruction+get_jump_loc(binary.substring(6));
					return instruction;
				//I-Types
				case 4:
					instruction=instruction+"beq\t";
					instruction=instruction+get_reg(binary.substring(6, 11))+get_reg(binary.substring(11,16));
					return instruction+calculate_imm(binary.substring(18));
				case 5:
					instruction=instruction+"bne\t";
					instruction=instruction+get_reg(binary.substring(6, 11))+get_reg(binary.substring(11,16));
					return instruction+calculate_imm(binary.substring(18));
				case 8:
					instruction=instruction+"addi\t";
					break;
				case 9:
					instruction=instruction+"addiu\t";
					break;
				case 10:
					instruction=instruction+"slti\t";
					break;
				case 11:
					instruction=instruction+"sltiu\t";
					break;
				case 12:
					instruction=instruction+"andi\t";
					break;
				case 13:
					instruction=instruction+"ori\t";
					break;
				case 14:
					instruction=instruction+"xori\t";
					break;
				case 15:
					instruction=instruction+"lui\t";
					break;
				case 32:
					instruction=instruction+"lb\t";
					break;
				case 33:
					instruction=instruction+"lh\t";
					break;
				case 35:
					instruction=instruction+"lw\t";
					break;
				case 36:
					instruction=instruction+"lbu\t";
					break;
				case 37:
					instruction=instruction+"lhu\t";
					break;
				case 40:
					instruction=instruction+"sb\t";
					break;
				case 41:
					instruction=instruction+"sh\t";
					break;
				case 43:
					instruction=instruction+"sw\t";
					break;
				default:
					throw new InvalidOpException("No corresponding MIPS instruction!");
			}
			instruction=instruction+get_reg(binary.substring(11, 16))+get_reg(binary.substring(6,11));
			instruction=instruction+calculate_imm(binary.substring(18));
			if(instruction.charAt(instruction.length()-2)==',')
			{
				instruction=instruction.substring(0, instruction.length()-2); //Remove trailing commas
			}
		}
		catch(InvalidOpException ex)
		{
			System.out.println("Exception thrown: "+ex);
		}
		return instruction;
	}
	
	//Stores operations in data structure of instructions, in sequence
	public static void Load(String filename) throws IOException
	{
		try(BufferedReader fscan=new BufferedReader(new FileReader(filename))) 
		{
			String instruction="";
			while((instruction=fscan.readLine())!=null)
			{
				operations.add(instruction); //Store values in global data structure
			}
			fscan.close();
		}
		catch(IOException ex)
		{
			throw new IOException("An I/O Exception occured: "+ex);
		}
	}

	//Grabs next instruction based on PC
	public static String fetch()
	{
		//To get current op: (program_counter-PC Start)/4;
		String current_op=operations.get((program_counter-0x00400000)/4).toString();
		return current_op;
	}
	
	//Translate binary address into PC-accessible address
	public static String get_jump_loc(String address)
	{
		address="0000"+address+"00";
		address="0x"+String.format("%8s", Integer.toHexString(Integer.parseInt(address, 2))).replace(' ', '0');
		return address;
	}
	
	//If opcode is r-type, we come to this lookup to find the function to run
	public static String get_function(String funct_code) throws InvalidOpException
	{
		String func="";
		switch(Integer.parseInt(funct_code.substring(20), 2)) //function code lookup table
		{
			case 0:
				func="sll\t";
				break;
			case 2:
				func="srl\t";
				break;
			case 3:
				func="sra\t";
				break;
			case 4:
				func="sllv\t";
				break;
			case 6:
				func="srlv\t";
				break;
			case 7:
				func="srav\t";
				break;
			case 8:
				func="jr\t"+get_reg(funct_code.substring(0,5));
				func=func.substring(0, func.length()-2); //Trim trailing comma
				return func;
			case 9:
				func="jalr\t";
				break;
			case 12:
				return "syscall"; //there are no requirements, so we return
				//break;
			case 13:
				return "break"; //there are no requirements, so we return
				//break;
			case 32:
				func="add\t";
				break;
			case 33:
				func="addu\t";
				break;
			case 34:
				func="sub\t";
				break;
			case 35:
				func="subu\t";
				break;
			case 36:
				func="and\t";
				break;
			case 37:
				func="or\t";
				break;
			case 38:
				func="xor\t";
				break;
			case 39:
				func="nor\t";
				break;
			case 42:
				func="slt\t";
				break;
			case 43:
				func="sltu\t";
				break;
			default:
				throw new InvalidOpException("No corresponding MIPS instruction!");
		}
		func=func+get_reg(funct_code.substring(10, 15))+get_reg(funct_code.substring(0, 5))+get_reg(funct_code.substring(5, 10)); //Decode all three registers
		if(func.charAt(func.length()-2)==',')
		{
			func=func.substring(0, func.length()-2); //remove trailing comma
		}
		return func;
	}
	
	//Change binary register address to valid register, using global array
	public static String get_reg(String rd) throws InvalidOpException
	{
		try
		{
			rd=register_list[Integer.parseInt(rd,2)]+", ";
		}
		catch(ArrayIndexOutOfBoundsException ex)
		{
			throw new InvalidOpException("Register address is not valid!");
		}
		return rd;
	}
	
	//Change binary immediate value to decimal
	public static String calculate_imm(String immediate)
	{
		Integer fromString=new Integer(Integer.parseInt(immediate, 2));
		return Integer.toString(fromString.byteValue()); //convert to signed 8-bit value
	}
}

//Custom exception, used in case machine code is invalid
class InvalidOpException extends Exception  //If a register address, op code, or function code is invalid, we throw this
{
	private static final long serialVersionUID=1L;
	public InvalidOpException(String msg)
	{
		super(msg);
	}
}