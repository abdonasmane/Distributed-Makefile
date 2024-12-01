list.txt:	list1 list2 list3 list4 list5 list6 list7 list8 list9 list10 list11.txt list12.txt list13.txt list14.txt list15.txt list16.txt list17.txt list18.txt list19.txt list20.txt 
	cp list1.txt list.txt ; cat list2.txt >> list.txt ; cat list3.txt >> list.txt ; cat list4.txt >> list.txt ; cat list5.txt >> list.txt ; cat list6.txt >> list.txt ; cat list7.txt >> list.txt ; cat list8.txt >> list.txt ; cat list9.txt >> list.txt ; cat list10.txt >> list.txt ; cat list11.txt >> list.txt ; cat list12.txt >> list.txt ; cat list13.txt >> list.txt ; cat list14.txt >> list.txt ; cat list15.txt >> list.txt ; cat list16.txt >> list.txt ; cat list17.txt >> list.txt ; cat list18.txt >> list.txt ; cat list19.txt >> list.txt ; cat list20.txt >> list.txt ;

premiera:	premier.c
	gcc premier.c -o premier -lm
list1:    premiera
	./premier 2 `echo 1*200000000/20-1 |bc` > list1.txt

list2:    premiera
	./premier `echo 1*200000000/20 |bc` `echo 2*200000000/20-1 |bc` > list2.txt

list3:    premiera
	./premier `echo 2*200000000/20 |bc` `echo 3*200000000/20-1 |bc` > list3.txt

list4:    premiera
	./premier `echo 3*200000000/20 |bc` `echo 4*200000000/20-1 |bc` > list4.txt

list5:    premiera
	./premier `echo 4*200000000/20 |bc` `echo 5*200000000/20-1 |bc` > list5.txt

list6:    premiera
	./premier `echo 5*200000000/20 |bc` `echo 6*200000000/20-1 |bc` > list6.txt

list7:    premiera
	./premier `echo 6*200000000/20 |bc` `echo 7*200000000/20-1 |bc` > list7.txt

list8:    premiera
	./premier `echo 7*200000000/20 |bc` `echo 8*200000000/20-1 |bc` > list8.txt

list9:    premiera
	./premier `echo 8*200000000/20 |bc` `echo 9*200000000/20-1 |bc` > list9.txt

list10:    premiera
	./premier `echo 9*200000000/20 |bc` `echo 10*200000000/20-1 |bc` > list10.txt

list11.txt:    premiera
	./premier `echo 10*200000000/20 |bc` `echo 11*200000000/20-1 |bc` > list11.txt

list12.txt:    premiera
	./premier `echo 11*200000000/20 |bc` `echo 12*200000000/20-1 |bc` > list12.txt

list13.txt:    premiera
	./premier `echo 12*200000000/20 |bc` `echo 13*200000000/20-1 |bc` > list13.txt

list14.txt:    premiera
	./premier `echo 13*200000000/20 |bc` `echo 14*200000000/20-1 |bc` > list14.txt

list15.txt:    premiera
	./premier `echo 14*200000000/20 |bc` `echo 15*200000000/20-1 |bc` > list15.txt

list16.txt:    premiera
	./premier `echo 15*200000000/20 |bc` `echo 16*200000000/20-1 |bc` > list16.txt

list17.txt:    premiera
	./premier `echo 16*200000000/20 |bc` `echo 17*200000000/20-1 |bc` > list17.txt

list18.txt:    premiera
	./premier `echo 17*200000000/20 |bc` `echo 18*200000000/20-1 |bc` > list18.txt

list19.txt:    premiera
	./premier `echo 18*200000000/20 |bc` `echo 19*200000000/20-1 |bc` > list19.txt

list20.txt:    premiera
	./premier `echo 19*200000000/20 |bc` `echo 20*200000000/20-1 |bc` > list20.txt



