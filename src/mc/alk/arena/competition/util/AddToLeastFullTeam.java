package mc.alk.arena.competition.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mc.alk.arena.Defaults;
import mc.alk.arena.competition.Competition;
import mc.alk.arena.competition.events.Event.TeamSizeComparator;
import mc.alk.arena.objects.ArenaSize;
import mc.alk.arena.objects.MatchParams;
import mc.alk.arena.objects.exceptions.NeverWouldJoinException;
import mc.alk.arena.objects.options.JoinOptions;
import mc.alk.arena.objects.options.JoinOptions.JoinOption;
import mc.alk.arena.objects.queues.TeamJoinObject;
import mc.alk.arena.objects.teams.ArenaTeam;
import mc.alk.arena.objects.teams.TeamFactory;

public class AddToLeastFullTeam extends TeamJoinHandler {

	public AddToLeastFullTeam(MatchParams params, Competition competition, Class<? extends ArenaTeam> clazz) throws NeverWouldJoinException{
		super(params,competition,clazz);
		if (minTeams == ArenaSize.MAX || maxTeams == ArenaSize.MAX)
			throw new NeverWouldJoinException("If you add players by adding them to the next team in the list, there must be a finite number of players");
		/// Lets add in all our teams first
		if (minTeams > Defaults.MAX_TEAMS)
			throw new NeverWouldJoinException("You can't make more than "+Defaults.MAX_TEAMS +" teams");
		for (int i=0;i<minTeams;i++){
			ArenaTeam team = TeamFactory.createTeam(clazz);
			team.setCurrentParams(params);
			addTeam(team);

		}
	}

	@Override
	public TeamJoinResult joiningTeam(TeamJoinObject tqo) {
		ArenaTeam team = tqo.getTeam();
		if (team.size()==1){
			ArenaTeam oldTeam = addToPreviouslyLeftTeam(team.getPlayers().iterator().next());
			if (oldTeam != null)
				return new TeamJoinResult(TeamJoinStatus.ADDED_TO_EXISTING,minTeamSize - oldTeam.size(), oldTeam);
		}
		if ( maxTeamSize < team.size()){
			return CANTFIT;}
		/// Try to let them join their specified team if possible
		JoinOptions jo = tqo.getJoinOptions();
		if (jo != null && jo.hasOption(JoinOption.TEAM)){
			Integer index = (Integer) jo.getOption(JoinOption.TEAM);
			if (index < maxTeams){ /// they specified a team index within range
				ArenaTeam baseTeam= teams.get(index);
				TeamJoinResult tjr = teamFits(baseTeam, team);
				if (tjr != CANTFIT)
					return tjr;
			}
		}
		boolean hasZero = false;
		for (ArenaTeam t : teams){
			if (t.size() == 0){
				hasZero = true;
				break;
			}
		}
		/// Since this is nearly the same as BinPack add... can we merge somehow easily?
		if (!hasZero && teams.size() < maxTeams){
			ArenaTeam ct = TeamFactory.createTeam(clazz);
			ct.setCurrentParams(tqo.getMatchParams());
			ct.addPlayers(team.getPlayers());
			if (ct.size() >= minTeamSize){
				addTeam(ct);
				return new TeamJoinResult(TeamJoinStatus.ADDED, minTeamSize - ct.size(),ct);
			} else {
				pickupTeams.add(ct);
				TeamJoinResult ar = new TeamJoinResult(TeamJoinStatus.WAITING_FOR_PLAYERS, minTeamSize - ct.size(),ct);
				return ar;
			}
		} else {
			/// Try to fit them with an existing team
			List<ArenaTeam> sortedBySize = new ArrayList<ArenaTeam>(teams);
			Collections.sort(sortedBySize, new TeamSizeComparator());
			for (ArenaTeam baseTeam : sortedBySize){
				TeamJoinResult tjr = teamFits(baseTeam, team);
				if (tjr != CANTFIT)
					return tjr;
			}
			/// sorry peeps.. full up
			return CANTFIT;
		}
	}

	private TeamJoinResult teamFits(ArenaTeam baseTeam, ArenaTeam team) {
		if ( baseTeam.size() + team.size() <= maxTeamSize){
			addToTeam(baseTeam, team.getPlayers());
			if (baseTeam.size() == 0){
				return new TeamJoinResult(TeamJoinStatus.ADDED, minTeamSize - baseTeam.size(),baseTeam);
			} else {
				return new TeamJoinResult(TeamJoinStatus.ADDED_TO_EXISTING, minTeamSize - baseTeam.size(),baseTeam);
			}
		}
		return CANTFIT;
	}

	@Override
	public String toString(){
		return "["+competition.getParams().getName() +":JH:AddToLeast]";
	}
}
