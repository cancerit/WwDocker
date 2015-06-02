#!/usr/bin/perl
#
# @File run.pl
# @Author kr2
# @Created 02-Jun-2015 14:02:36
#

use strict;

die qq{NAN\n} unless($ARGV[0] =~ m/^\d+$/);
my $splurge = (q{X}x20).qq{\n};
for(1..$ARGV[0]) {
    print $splurge;
    print STDERR $splurge;
}
