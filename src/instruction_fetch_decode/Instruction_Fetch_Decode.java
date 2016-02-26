package instruction_fetch_decode;
import java.io.*;
import java.util.ArrayList;

/*I've managed to make spaghetti out of this, good luck.*/

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
	public static int[] register_values=new int[32]; //Global so there's no initialization on every get_registers call, initialized with zeroes.
	public static int RegDst, ALUSrc, MemToReg, RegWrite, Branch, 
					  PCSrc, ReadAddr1, ReadAddr2, WriteAddr, ALUControl, 
					  ZeroALU, MemWrite, MemRead, ALUOp;
	public static void main(String[] args)
	{
		String decoded, fetched, binary, output_str="";
		try
		{
			BufferedWriter output=new BufferedWriter(new FileWriter("HW2Diagnostics.txt"));
			Load("HW2MachineCode.txt");
			output.write("PC,\tInstruction,\tArgs,\tRegDst,\tALUSrc,\tMemToReg,\tRegWrite,\tMemRead,\tMemWrite,\tBranch,\tPCSrc,\tALUOp,\tALUControl,\t$v0,\t$a0,\t$t0"+"\n");
			for(int i=0;i<operations.size();i++)
			{
				fetched=fetch();
				decoded=decode(fetched); //Pull machine code instruction from list, translate
				fetched=fetched.substring(2);
				if(decoded=="syscall")
				{
					syscall();
				}
				else
				{
					binary=String.format("%32s", Integer.toBinaryString(Integer.parseInt(fetched, 16))).replace(' ', '0');
					control(binary);
					alu_control(binary);
					ex(decoded.substring(0,5).trim());
					mem();
					wb();
				}
				output_str="0x"+String.format("%8s", Integer.toHexString(program_counter)).replace(' ', '0')+","+decoded.replaceFirst("\\s", ",");
				output_str = (output_str.toLowerCase().contains("syscall")) ? (output_str+",0") : (output_str) ;
				output_str=output_str+",\t"+RegDst+",\t"+ALUSrc+",\t"+MemToReg+",\t"+RegWrite+",\t"+
						   MemRead+",\t"+MemWrite+",\t"+Branch+",\t"+PCSrc+",\t"+ALUOp+",\t"+
						   ALUControl+",\t"+register_values[2]+",\t"+register_values[4]+",\t"+register_values[8];
				output.write(output_str+"\n");
				program_counter+=4; //Step to next instruction
			}
			output.close();
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
			instruction="";
			hex=hex.substring(2); //remove 0x
			int i=Integer.parseInt(hex, 16);
			String binary=String.format("%32s", Integer.toBinaryString(i)).replace(' ', '0'); //Ensure when it is translated to binary, we don't lose precision
			String binary_op=binary.substring(0, 6);
			ReadAddr2=Integer.parseInt(get_jump_loc(binary.substring(6)).substring(2), 16);
			switch(Integer.parseInt(binary_op, 2)) //Op code lookup table
			{
				//R-Types
				case 0:
					try
					{
						instruction=instruction+get_function(binary.substring(6));
					}
					catch(InvalidOpException ex)
					{
						System.out.println("Exception thrown: "+ex);
					}
					break;
				//J-Types
				case 2:
					instruction=instruction+"j ";
					instruction=instruction+ReadAddr2;
					program_counter=ReadAddr2-4;
					ReadAddr2=0;
					break;
				case 3:
					instruction=instruction+"jal ";
					instruction=instruction+ReadAddr2;
					ReadAddr1=program_counter;
					program_counter=ReadAddr2-4;
					ReadAddr2=0;
					break;
				//I-Types
				case 4:
					ReadAddr1=get_reg(binary.substring(11,16));
					ReadAddr2=get_reg(binary.substring(6, 11));
					instruction=instruction+"beq "+register_list[ReadAddr2]+" "+register_list[ReadAddr1];
					instruction=instruction+" "+calculate_imm(binary.substring(18));
					break;
				case 5:
					ReadAddr1=get_reg(binary.substring(6, 11));
					ReadAddr2=get_reg(binary.substring(11,16));
					instruction=instruction+"bne "+register_list[ReadAddr2]+" "+register_list[ReadAddr1];
					instruction=instruction+" "+calculate_imm(binary.substring(18));
					break;
				case 8:
					ReadAddr1=get_reg(binary.substring(11, 16));
					ReadAddr2=get_reg(binary.substring(6,11));
					WriteAddr=program_counter+4*Integer.parseInt(calculate_imm(binary.substring(18)));
					instruction=instruction+"addi "+register_list[ReadAddr1]+" "+register_list[ReadAddr2];
					instruction=instruction+" "+calculate_imm(binary.substring(18));
					break;
				case 9:
					instruction=instruction+"addiu ";
					break;
				case 10:
					instruction=instruction+"slti ";
					break;
				case 11:
					instruction=instruction+"sltiu ";
					break;
				case 12:
					instruction=instruction+"andi ";
					break;
				case 13:
					instruction=instruction+"ori ";
					break;
				case 14:
					instruction=instruction+"xori ";
					break;
				case 15:
					instruction=instruction+"lui ";
					break;
				case 32:
					instruction=instruction+"lb ";
					break;
				case 33:
					instruction=instruction+"lh ";
					break;
				case 35:
					instruction=instruction+"lw ";
					break;
				case 36:
					instruction=instruction+"lbu ";
					break;
				case 37:
					instruction=instruction+"lhu ";
					break;
				case 40:
					instruction=instruction+"sb ";
					break;
				case 41:
					instruction=instruction+"sh ";
					break;
				case 43:
					instruction=instruction+"sw ";
					break;
				default:
					throw new InvalidOpException("No corresponding MIPS instruction!");
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
				func="sll ";
				break;
			case 2:
				func="srl ";
				break;
			case 3:
				func="sra ";
				break;
			case 4:
				func="sllv ";
				break;
			case 6:
				func="srlv ";
				break;
			case 7:
				func="srav ";
				break;
			case 8:
				func="jr ";
				WriteAddr=get_reg(funct_code.substring(0,5));
				func=func+" "+register_list[WriteAddr];
				break;
			case 9:
				func="jalr ";
				break;
			case 12:
				return "syscall"; //there are no requirements, so we return
			case 13:
				return "break"; //there are no requirements, so we return
			case 32:
				func="add ";
				WriteAddr = get_reg(funct_code.substring(10, 15));
				ReadAddr1 = get_reg(funct_code.substring(0, 5));
				ReadAddr2 = get_reg(funct_code.substring(5, 10));
				func=func+register_list[WriteAddr]+" "+register_list[ReadAddr1]+" "+register_list[ReadAddr2]; //Decode all three registers
				break;
			case 33:
				func="addu ";
				break;
			case 34:
				func="sub ";
				break;
			case 35:
				func="subu ";
				break;
			case 36:
				func="and ";
				break;
			case 37:
				func="or ";
				break;
			case 38:
				func="xor ";
				break;
			case 39:
				func="nor ";
				break;
			case 42:
				func="slt ";
				break;
			case 43:
				func="sltu ";
				break;
			default:
				throw new InvalidOpException("No corresponding MIPS instruction!");
		}
		return func;
	}
	
	//Change binary register address to valid register, using global array
	public static int get_reg(String rd)
	{
		return Integer.parseInt(rd,2);
	}
	
	//Change binary immediate value to decimal
	public static String calculate_imm(String immediate)
	{
		Integer fromString=new Integer(Integer.parseInt(immediate, 2));
		return Integer.toString(fromString.byteValue()); //convert to signed 8-bit value
	}
	
	public static void control(String instruction)
	{
		RegDst = ALUSrc = MemToReg = RegWrite = Branch = PCSrc = 0;
		switch(Integer.parseInt(instruction.substring(0, 8), 2))
		{
			case 0:
				switch(Integer.parseInt(instruction.substring(26), 2))
				{
				case 8:
					PCSrc=3;
					break;
				case 32:
					RegDst=RegWrite=1;
					break;
				default: //captures syscall
					break;
				}
				break;
			case 2:
				PCSrc=2;
				break;
			case 3:
				RegDst=2;
				MemToReg=2;
				RegWrite=1;
				PCSrc=2;
				break;
			case 4:
				Branch=1;
				PCSrc=ZeroALU;
				break;
			case 8:
				ALUSrc=1;
				RegWrite=1;
				break;
			default:
				break;
		}
	}
	
	//id() was integrated with decode()
	
	public static void alu_control(String instruction)
	{
		String fct="";
		String Op=instruction.substring(0, 6);
		switch(Integer.parseInt(Op,2))
		{
			case 0:
				fct=instruction.substring(26);
				switch(Integer.parseInt(fct,2))
				{
					case 0:
						ALUControl=3;
						break;
					case 2:
						ALUControl=4;
						break;
					case 3:
						ALUControl=5;
						break;
					case 4:
						ALUControl=3;
						break;
					case 6:
						ALUControl=4;
						break;
					case 7:
						ALUControl=5;
						break;
					case 32:
						ALUControl=2;
						break;
					case 33:
						ALUControl=2;
						break;
					case 34:
						ALUControl=6;
						break;
					case 35:
						ALUControl=6;
						break;
					case 36:
						ALUControl=0;
						break;
					case 37:
						ALUControl=1;
						break;
					case 38:
						ALUControl=9;
						break;
					case 39:
						ALUControl=8;
						break;
					case 42:
						ALUControl=7;
						break;
					case 43:
						ALUControl=7;
						break;
					default:
						break;
				}
			case 4:
				ALUControl=6;
				break;
			case 5:
				ALUControl=6;
				break;
			case 8:
				ALUControl=2;
				break;
			case 9:
				ALUControl=2;
				break;
			case 10:
				ALUControl=7;
				break;
			case 11:
				ALUControl=7;
				break;
			case 12:
				ALUControl=0;
				break;
			case 13:
				ALUControl=1;
				break;
			case 14:
				ALUControl=9;
				break;
			case 15:
				ALUControl=3;
				break;
			case 32:
				ALUControl=2;
				break;
			case 33:
				ALUControl=2;
				break;
			case 35:
				ALUControl=2;
				break;
			case 36:
				ALUControl=2;
				break;
			case 37:
				ALUControl=2;
				break;
			case 40:
				ALUControl=2;
				break;
			case 41:
				ALUControl=2;
				break;
			case 43:
				ALUControl=2;
				break;
			default:
				break;
			}
		ALUOp=ALUControl;
	}
	
	public static void ex(String op)
	{
		switch(ALUControl)
		{
			case 1:
				ZeroALU=ReadAddr1 & ReadAddr2;
				break;
			case 2:
				ZeroALU=ReadAddr1 | ReadAddr2;
				break;
			case 3:
				ZeroALU=ReadAddr1 + ReadAddr2;
				break;
			case 4:
				ZeroALU = ReadAddr1 << 10;
				break;
			case 5:
				ZeroALU = ReadAddr1 >> 10;
				break;
			case 6:
				ZeroALU=ReadAddr1-ReadAddr2;
				ZeroALU = (op=="beq"&&ZeroALU==0) ? 1 : 0;
				ZeroALU = (op=="bne"&&ZeroALU!=0) ? 1 : 0;
				if(ZeroALU==1)
				{
					program_counter+=4*ReadAddr2;
				}
				break;
			case 7:
				//slt
				break;
			case 8:
				//nor
				break;
			case 9:
				//xor
				break;
			default:
				//shouldn't get here, ever
				break;
		}
	}
	
	public static void mem()
	{
		//does nothing
	}
	
	public static void wb()
	{
		if(RegWrite==1)
		{
			//register_values[WriteAddr]=register_values[ReadAddr1]+register_values[ReadAddr2]; //OutOfBoundsException here
		}
	}
	
	public static void syscall()
	{
		if (register_values[2]==10)
		{
			System.out.println("Program terminated");
			System.exit(0); //Terminate program
		}
		else if(register_values[2]==10)
		{
			System.out.println(register_values[4]);
		}
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